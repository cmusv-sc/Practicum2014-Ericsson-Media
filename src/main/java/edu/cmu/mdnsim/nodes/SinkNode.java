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

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.util.Utility;

public class SinkNode extends AbstractNode implements PortBindable{

	private Map<String, DatagramSocket> streamIdToSocketMap = new HashMap<String, DatagramSocket>();
	/**
	 *  Key: FlowId; Value: ReceiveThread 
	 */
	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler>();

	public SinkNode() throws UnknownHostException {
		super();
	}


	@Override
	public int bindAvailablePortToFlow(String streamId) {

		if (streamIdToSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamIdToSocketMap.get(streamId).getPort();
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

			streamIdToSocketMap.put(streamId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}

	@Override
	public void executeTask(Stream stream) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.executeTask(): Sink received a StreamSpec.");
		}

		for(Flow flow : stream.getFlowList()){
			for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {
				if (nodePropertiesMap.get(Flow.NODE_ID).equals(getNodeId())) {
					Integer port = bindAvailablePortToFlow(flow.getStreamId());
					
					lanchReceiveRunnable(stream, Integer.valueOf(stream.getDataSize()), Integer.valueOf(flow.getKiloBitRate()));
					System.out.println("[SINK] UpStream Node Id" + flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID)));
					//Send the stream spec to upstream uri
					Map<String, String> upstreamNodePropertiesMap = flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
					upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, super.getHostAddr().getHostAddress()+":"+port);
					try {
						msgBusClient.send("/tasks", nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
					break;
				}
			}
		}

	}

	/**
	 * Create and Launch a ReceiveRunnable thread & record it in the map
	 * @param streamId
	 */
	public void lanchReceiveRunnable(Stream stream, int totalData, int rate){

		ReceiveRunnable rcvRunnable = new ReceiveRunnable(stream, totalData, rate);
		Future rcvFuture = ThreadPool.executeAfter(new MDNTask(rcvRunnable), 0);
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(rcvFuture, rcvRunnable));

	}

	@Override
	public void terminateTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.terminateTask(): " + JSON.toJSON(flow));
		}

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getFlowId());
		if(streamTaskHandler == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		streamTaskHandler.kill();

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

		StreamTaskHandler rcvThread = streamIdToRunnableMap.get(flow.getFlowId());
		while (!rcvThread.isDone());
		rcvThread.clean();
		streamIdToRunnableMap.remove(flow.getFlowId());
	}

	@Override
	public void cleanUp() {

		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {

			streamTask.kill();
			while(!streamTask.isDone());
			streamTask.clean();
			streamIdToRunnableMap.remove(streamTask.getStreamId());
			System.out.println("[DEBUG]SinkNode.cleanUp(): Stops NodeRunnable for stream:" + streamTask.getStreamId());

		}

		msgBusClient.removeResource("/" + getNodeId());

	}

	/**
	 * 
	 * Each flow is received in a separate WarpPoolThread.
	 * After receiving all packets from the source, this thread 
	 * reports the total time and total number of bytes received by the 
	 * sink node back to the master using the message bus.
	 * 
	 * @param streamId The flowId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	private class ReceiveRunnable extends NodeRunnable {

		private DatagramSocket receiveSocket;

		private DatagramPacket packet;

		private int totalData;
		
		private int rate;
		public ReceiveRunnable(Stream stream, int totalData, int rate) {
			super(stream, msgBusClient, getNodeId());
			this.totalData = totalData;
			this.rate = rate;
		}

		@Override
		public void run() {						

			if(!initializeSocketAndPacket()){
				return;
			}
			
			PacketLostTracker packetLostTracker = new PacketLostTracker(totalData, rate, NodePacket.PACKET_MAX_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND);

			long startedTime = 0;
			boolean isFinalWait = false;			
			TaskHandler reportTaksHandler = null;

			while (!isKilled()) {
				try{
					receiveSocket.receive(packet);
				} catch(SocketTimeoutException ste){
					//ste.printStackTrace();
					if(this.isUpStreamDone()){
						if(!isFinalWait){
							isFinalWait = true;
							continue;
						}else{
							packetLostTracker.updatePacketLostForTimeout();
							//setLostPacketNum(this.getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (expectedMaxPacketId - highPacketIdBoundry));
							break;		
						}
					}					
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
				
				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

				NodePacket nodePacket = new NodePacket(packet.getData());
				packetLostTracker.updatePacketLost(nodePacket.getMessageId());
				
				if(startedTime == 0){
					startedTime = System.currentTimeMillis();
					reportTaksHandler = createAndLaunchReportTransportationRateRunnable(packetLostTracker);					
					report(startedTime, -1, getTotalBytesTranfered(), EventType.RECEIVE_START);
				}
				
				if(nodePacket.isLast()){
					break;
				}
			}	
			long endTime= System.currentTimeMillis();
				report(startedTime, endTime, getTotalBytesTranfered(), EventType.RECEIVE_END);

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
			receiveSocket = streamIdToSocketMap.get(getStreamId());
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
		private TaskHandler createAndLaunchReportTransportationRateRunnable(PacketLostTracker packetLostTracker){	
			ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
				//WarpThreadPool.executeCached(reportTransportationRateRunnable);
				Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
				return new TaskHandler(reportFuture, reportTransportationRateRunnable);
		}

		private void report(long startTime, long endTime, int totalBytes, EventType eventType){
			System.out.println("[SINK] Reporting to master StreamId:" + getStreamId());
			SinkReportMessage sinkReportMsg = new SinkReportMessage();
			sinkReportMsg.setStreamId(getStreamId());
			sinkReportMsg.setTotalBytes(totalBytes);
			sinkReportMsg.setTime(Utility.millisecondTimeToString(endTime));
			sinkReportMsg.setDestinationNodeId(this.getUpStreamId());
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
						+ sinkReportMsg.getStreamId()
						+ " Total bytes " + sinkReportMsg.getTotalBytes() 
						+ " Total Time:" + ((endTime - startTime) / 1000)
						+ "(sec)");
			}
		}

		private void clean() {
			if(receiveSocket != null && !receiveSocket.isClosed()){
				receiveSocket.close();
			}
			if(streamIdToSocketMap.containsKey(getStreamId())){
				streamIdToSocketMap.remove(getStreamId());
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

		@Override
		protected void sendEndMessageToDownstream() {
			
			
		}

	}


	private class StreamTaskHandler {
		private Future streamFuture;
		private ReceiveRunnable streamTask;

		public StreamTaskHandler(Future streamFuture, ReceiveRunnable streamTask) {
			this.streamFuture = streamFuture;
			this.streamTask = streamTask;
		}

		public void kill() {
			streamTask.kill();
		}

		public boolean isDone() {
			return streamFuture.isDone();
		}

		public void clean() {
			streamTask.clean();
		}

		public String getStreamId() {
			return streamTask.getStreamId();
		}
	}
}
