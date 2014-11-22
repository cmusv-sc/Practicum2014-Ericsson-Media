package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;
import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {

	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new HashMap<String, StreamTaskHandler>();

	public SourceNode() throws UnknownHostException {
		super();
	}	
	/**
	 * It is assumed that there will be only one downstream node for one stream
	 * even if Source node exists in multiple flows.
	 */
	@Override
	public void executeTask(Stream stream) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.executeTask(): Source received a work specification.");
		}
		//All the flows in the stream should have source node in it.
		//And the properties for source node should be same in all flows.
		Flow flow = stream.getFlowList().get(0);
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		if (nodePropertiesMap.get(Flow.NODE_ID).equals(getNodeId())) {
			String[] ipAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
			String destAddrStr = ipAndPort[0];
			int destPort = Integer.parseInt(ipAndPort[1]);
			int dataSize = Integer.parseInt(flow.getDataSize());
			int rate = Integer.parseInt(flow.getKiloBitRate());

			//Get up stream and down stream node ids
			//As of now Source Node does not have upstream id
			//upStreamNodes.put(streamSpec.StreamId, nodeProperties.get("UpstreamId"));
			//downStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get(Flow.DOWNSTREAM_ID));

			try {
				createAndLaunchSendRunnable(stream, InetAddress.getByName(destAddrStr), destPort, 
						dataSize, rate);					
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}	
		}
	}

	/**
	 * Create and launch a SendRunnable and put it in the streamId - runnable map
	 * @param streamId
	 * @param destAddrStr
	 * @param destPort
	 * @param bytesToTransfer
	 * @param rate
	 */
	public void createAndLaunchSendRunnable(Stream stream, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate){
		SendRunnable sendRunnable = new SendRunnable(stream, destAddrStr, destPort, bytesToTransfer, rate);
		Future sendFuture = ThreadPool.executeAfter(new MDNTask(sendRunnable), 0);
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(sendFuture, sendRunnable));
	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void terminateTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.terminateTask(): Source received terminate task.\n" + JSON.toJSON(flow));
		}

		StreamTaskHandler sendTaskHanlder = streamIdToRunnableMap.get(flow.getFlowId());

		if(sendTaskHanlder == null){

			throw new TerminateTaskBeforeExecutingException();

		}

		sendTaskHanlder.kill();

		releaseResource(flow);

	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void releaseResource(Flow flow) {

		StreamTaskHandler sndThread = streamIdToRunnableMap.get(flow.getFlowId());
		while (!sndThread.isDone());
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.releaseResource(): Source starts to clean-up resource.");
		}

		sndThread.clean();
		streamIdToRunnableMap.remove(flow.getFlowId());

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());

		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void cleanUp() {
		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {
			streamTask.kill();
			while(!streamTask.isDone());
			streamTask.clean();
			streamIdToRunnableMap.remove(streamTask.getStreamId());
			System.out.println("[DEBUG]SourceNode.cleanUp(): Stops streamRunnable:" + streamTask.getStreamId());
		}
		msgBusClient.removeResource("/" + getNodeId());
	}

	private class SendRunnable extends NodeRunnable {

		private DatagramSocket sendSocket = null;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private DatagramPacket packet;

		public SendRunnable(Stream stream, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate) {
			super(stream, msgBusClient, getNodeId());
			this.dstAddrStr = dstAddrStr;
			this.dstPort = dstPort;
			this.bytesToTransfer = bytesToTransfer;
			this.rate = rate;	
		}

		/**
		 * The method will send a packet in the following order:
		 * 1. Calculate the packet number per second based on the user specified sending rate.
		 * 2. Calculates the time expected to send one package in millisecond.
		 * 3. Send one packet, if the actual sending time is less than expected time, it will sleep for the gap
		 * 					   else, do nothing. In this case, the use specified rate is higher than the highest rate in real
		 * 
		 */
		@Override
		public void run() {

			if(!initializeSocketAndPacket()){
				return;
			}

			double packetPerSecond = rate / NodePacket.PACKET_MAX_LENGTH;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 

			report(EventType.SEND_START);

			TaskHandler reportTaskHandler = null;	
			long startedTime = 0;
			int packetId = 0;

			while (bytesToTransfer > 0 && !isKilled()) {	
				long begin = System.currentTimeMillis();

				NodePacket nodePacket = bytesToTransfer <= NodePacket.PACKET_MAX_LENGTH ? new NodePacket(1, packetId, bytesToTransfer) : new NodePacket(0, packetId);
				packet.setData(nodePacket.serialize());
				Random random = new Random();
				try {
					if(random.nextDouble() > 0.5){
						sendSocket.send(packet);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				if(startedTime == 0){
					startedTime = System.currentTimeMillis();
					reportTaskHandler = createAndLaunchReportTransportationRateRunnable();
				}

				bytesToTransfer -= packet.getLength();
				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

				long end = System.currentTimeMillis();
				long millisRemaining = millisecondPerPacket - (end - begin);
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
				packetId++;
			}

			report(EventType.SEND_END);
			this.sendEndMessageToDownstream();

			if (ClusterConfig.DEBUG) {
				if (isKilled()) {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has been killed(not finished yet).");
				} else{
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has finished.");
				}
			}

			if(reportTaskHandler != null){
				reportTaskHandler.kill();
			}
			clean();
			stop();
		}




		/**
		 * Initialize the send socket and the DatagramPacket 
		 * @return true, successfully done
		 * 		   false, failed in some part
		 */
		private boolean initializeSocketAndPacket(){
			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException socketException) {
				return false;
			}

			byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH];
			packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);

			return true;	
		}

		/**
		 * Create and Launch a report thread
		 * @return Future of the report thread
		 */
		private TaskHandler createAndLaunchReportTransportationRateRunnable(){
			ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, null);
			Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
			return new TaskHandler(reportFuture, reportTransportationRateRunnable);
		}

		/**
		 * Clean up all resources for this thread.
		 */
		public void clean() {
			if(sendSocket != null && !sendSocket.isClosed()){
				sendSocket.close();
			}
			if(streamIdToRunnableMap.containsKey(getStreamId())){
				streamIdToRunnableMap.remove(getStreamId());
			}
		}

		private void report(EventType eventType){

			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SourceNode.SendDataThread.run(): " + "Source will start sending data. " + "Record satrt time and report to master");
			}
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(getStreamId());
			srcReportMsg.setTotalBytesTransferred(bytesToTransfer);
			srcReportMsg.setTime(Utility.currentTime());	
			//It is assumed that Source node will have only one down stream node
			srcReportMsg.setDestinationNodeId(getDownStreamIds().iterator().next());
			srcReportMsg.setEventType(eventType);

			String fromPath = "/" + SourceNode.this.getNodeId() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "/source_report", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
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
			try {
				msgBusClient.send(getFromPath(), this.getDownStreamURIs().iterator().next()
						+ "/" + this.getStreamId(), "DELETE", this.getStream());
			} catch (MessageBusException e) {
				e.printStackTrace();
			}

		}
	}





	private class StreamTaskHandler {
		private Future streamFuture;
		private SendRunnable streamTask;

		public StreamTaskHandler(Future streamFuture, SendRunnable streamTask) {
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
