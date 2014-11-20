package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;
import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.mdnsim.nodes.AbstractNode.NodeRunnable.ReportRateRunnable;
import edu.cmu.util.Utility;

public class SinkNode extends AbstractNode implements PortBindable{

	private Map<String, DatagramSocket> flowIdToSocketMap = new HashMap<String, DatagramSocket>();
	/**
	 *  Key: FlowId; Value: ReceiveThread 
	 */
	private Map<String, ReceiveRunnable> streamIdToRunnableMap = new ConcurrentHashMap<String, ReceiveRunnable>();

	public SinkNode() throws UnknownHostException {
		super();
	}


	@Override
	public int bindAvailablePortToFlow(String flowId) {

		if (flowIdToSocketMap.containsKey(flowId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return flowIdToSocketMap.get(flowId).getPort();
		} else {
			
			DatagramSocket udpSocket = null;
			for(int i = 0; i < RETRY_CREATING_SOCKET_NUMBER; i++){
				try {
					udpSocket = new DatagramSocket(0, getHostAddr());
				} catch (SocketException e) {
					if (ClusterConfig.DEBUG) {
						System.out.println("Failed" + (i + 1) + "times to bind a port to a socket");
					}
					e.printStackTrace();
					continue;
				}
				break;
			}
			
			if(udpSocket == null){
				return -1;
			}
			
			flowIdToSocketMap.put(flowId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}

	@Override
	public void executeTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.executeTask(): Sink received a StreamSpec.");
		}

		int flowIndex = -1;

		for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {
			flowIndex++;
			if (nodePropertiesMap.get(Flow.NODE_ID).equals(getNodeId())) {
				Integer port = bindAvailablePortToFlow(flow.getFlowId());
				createAndLanchReceiveRunnable(flow.getFlowId());
				//Get up stream and down stream node ids
				//As of now Sink Node does not have downstream id
				upStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("UpstreamId"));
				//downStreamNodes.put(streamSpec.StreamId, nodeProperties.get("DownstreamId"));

				

				if (flowIndex+1 < flow.getNodeList().size()) {
					Map<String, String> upstreamFlow = flow.getNodeList().get(flowIndex+1);
					upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
					try {
						msgBusClient.send("/tasks", nodePropertiesMap.get("UpstreamUri") + "/tasks", "PUT", flow);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
				}
				break;
			}

		}

	}

	/**
	 * Create and Launch a ReceiveRunnable thread & record it in the map
	 * @param streamId
	 */
	public void createAndLanchReceiveRunnable(String streamId){

		ReceiveRunnable rcvRunnable = new ReceiveRunnable(streamId);
		streamIdToRunnableMap.put(streamId, rcvRunnable);
		if(integratedTest){
			ExecutorService executorService = Executors.newSingleThreadExecutor();
			executorService.execute(new ReceiveRunnable(streamId));
			executorService.shutdown();
		} else{
			WarpThreadPool.executeCached(rcvRunnable);			
		}
	}

	@Override
	public void terminateTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.terminateTask(): " + JSON.toJSON(flow));
		}

		ReceiveRunnable thread = streamIdToRunnableMap.get(flow.getFlowId());
		if(thread == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		thread.kill();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());

		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void releaseResource(Flow flow) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.releaseResource(): Sink starts to clean-up resource.");
		}

		ReceiveRunnable rcvThread = streamIdToRunnableMap.get(flow.getFlowId());
		while (!rcvThread.isStopped());
		rcvThread.clean();
		streamIdToRunnableMap.remove(flow.getFlowId());
	}

	/**
	 * 
	 * Each stream is received in a separate WarpPoolThread.
	 * After receiving all packets from the source, this thread 
	 * reports the total time and total number of bytes received by the 
	 * sink node back to the master using the message bus.
	 * 
	 * @param flowId The streamId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	 private class ReceiveRunnable extends NodeRunnable {

		private DatagramSocket receiveSocket;
		
		private DatagramPacket packet;

		public ReceiveRunnable(String flowId) {
			super(flowId);
		}

		@Override

		public void run() {						

			if(!initializeSocketAndPacket()){
				return;
			}
			
			long startedTime = 0;
			TaskHandler reportTaksHandler = null;
			
			while (!isKilled()) {
				try{
					receiveSocket.receive(packet);
				} catch(SocketTimeoutException ste){
					break;
				} catch (IOException e) {
					continue;
				} 

				if(startedTime == 0){
					startedTime = System.currentTimeMillis();
					reportTaksHandler = createAndLaunchReportTransportationRateRunnable();					
					if(!integratedTest){
						report(startedTime, -1, getTotalBytesTranfered(), EventType.RECEIVE_START);
					}
				}
				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());
				
				if (integratedTest) {
					System.out.println("[Sink] " + getTotalBytesTranfered() + " " + Utility.currentTime());		
				}
			}	
			long endTime= System.currentTimeMillis();
			if(!integratedTest){
				report(startedTime, endTime, getTotalBytesTranfered(), EventType.RECEIVE_END);
			} 
						
			if (ClusterConfig.DEBUG) {
				if (isKilled()) {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Killed.");
				} else {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Finish receiving.");
				}
			}
			if(reportTaksHandler != null){
				reportTaksHandler.kill();
			}
			clean();
			stop();
		}
		
		/**
		 * Initialize the receive socket and the DatagramPacket 
		 * @return true, successfully done
		 * 		   false, failed in some part
		 */
		private boolean initializeSocketAndPacket(){
			receiveSocket = flowIdToSocketMap.get(getFlowId());
			if (receiveSocket == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] SinkNode.ReceiveDataThread.run():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return false;
			}			
			try {
				receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
			} catch (SocketException e1) {
				return false;
			}

			byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH]; 
			packet = new DatagramPacket(buf, buf.length);

			return true;
		}

		
		/**
		 * Create and Launch a report thread
		 * @return Future of the report thread
		 */
		private TaskHandler createAndLaunchReportTransportationRateRunnable(){	
			ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND);
			if(integratedTest){
				ExecutorService executorService = Executors.newSingleThreadExecutor();
				Future reportFuture =  (Future) executorService.submit(reportTransportationRateRunnable);
				executorService.shutdown();
				return new TaskHandler(reportFuture, reportTransportationRateRunnable);
			} else {
				//WarpThreadPool.executeCached(reportTransportationRateRunnable);
				Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
				return new TaskHandler(reportFuture, reportTransportationRateRunnable);
			}	
		}

		private void report(long startTime, long endTime, int totalBytes, EventType eventType){
			System.out.println("[SINK] Reporting to master StreamId:" + getFlowId());
			SinkReportMessage sinkReportMsg = new SinkReportMessage();
			sinkReportMsg.setFlowId(getFlowId());
			sinkReportMsg.setTotalBytes(totalBytes);
			sinkReportMsg.setTime(Utility.millisecondTimeToString(endTime));
			sinkReportMsg.setDestinationNodeId(upStreamNodes.get(getFlowId()));
			sinkReportMsg.setEventType(eventType);

			String fromPath = SinkNode.super.getNodeId() + "/finish-rcv";

			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG]SinkNode.ReceiveThread.report(): Sink sends report to master.");
			}

			try {
				msgBusClient.sendToMaster(fromPath, "/sink_report", "POST", sinkReportMsg);
			} catch (MessageBusException e) {
				//TODO: add exception handler
				e.printStackTrace();
			}

			if (ClusterConfig.DEBUG) {
				System.out.println("[INFO]SinkNode.ReceiveDataThread.run(): " 
						+ "Sink finished receiving data at Stream-ID " 
						+ sinkReportMsg.getFlowId()
						+ " Total bytes " + sinkReportMsg.getTotalBytes() 
						+ " Total Time:" + ((endTime - startTime) / 1000)
						+ "(sec)");
			}
		}

		private void clean() {
			if(receiveSocket != null && !receiveSocket.isClosed()){
				receiveSocket.close();
			}
			if(flowIdToSocketMap.containsKey(getFlowId())){
				flowIdToSocketMap.remove(getFlowId());
			}
		}
		
		private class TaskHandler {
			
			Future reportFuture;
			ReportRateRunnable reportRunnable;
			
			public TaskHandler(Future future, ReportRateRunnable runnable) {
				this.reportFuture = future;
				reportRunnable = runnable;
			}
			
			public void kill() {
				reportRunnable.kill();
			}
			
			public boolean isDone() {
				return reportFuture.isDone();
			}
			
		}

	}
}
