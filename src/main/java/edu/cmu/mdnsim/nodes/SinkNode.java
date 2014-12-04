package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;

public class SinkNode extends AbstractNode{
	/**
	 *  Key: FlowId; Value: ReceiveThread 
	 */
	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler>();
	public SinkNode() throws UnknownHostException {
		super();
	}


	@Override
	public void executeTask(Message request, Stream stream) {

		logger.debug(this.getNodeId() + " Sink received a StreamSpec for Stream : " + stream.getStreamId());


		Flow flow = stream.findFlow(this.getFlowId(request));
		//Get the sink node properties
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		Integer port = this.getAvailablePort(flow.getStreamId());
		lanchReceiveRunnable(stream, flow);
		//Send the stream spec to upstream node
		Map<String, String> upstreamNodePropertiesMap = 
				flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
		upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, 
				super.getHostAddr().getHostAddress()+":"+port);

		try {
			msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
					nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
		} catch (MessageBusException e) {
			logger.debug("Could not send work config spec to upstream node." + e.toString());
		}
	}

	/**
	 * Create and Launch a ReceiveRunnable thread & record it in the map
	 * @param streamId
	 */
	public void lanchReceiveRunnable(Stream stream, Flow flow){
		ReceiveRunnable rcvRunnable = new ReceiveRunnable(stream, flow);
		Future<?> rcvFuture = NodeContainer.ThreadPool.submit(new MDNTask(rcvRunnable));
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(rcvFuture, rcvRunnable));
	}

	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());

		if(streamTaskHandler == null){
			throw new RuntimeException("Terminate task before executing");
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

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
		while (!streamTaskHandler.isDone());
		streamTaskHandler.clean();
		streamIdToRunnableMap.remove(flow.getStreamId());
	}

	@Override
	public synchronized void reset() {

		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {

			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();

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
		/**
		 * The flow for which the Sink is receiving data. 
		 * Each Sink Node will have only 1 flow associated with 1 stream.
		 */
		private Flow flow;
		public ReceiveRunnable(Stream stream, Flow flow) {
			super(stream, msgBusClient, getNodeId());
			this.flow = flow;
		}

		@Override
		public void run() {						

			if(!initializeSocketAndPacket()){
				return;
			}
			PacketLostTracker packetLostTracker = null;

			//			long startedTime = 0;
			boolean isFinalWait = false;			
			ReportTaskHandler reportTaksHandler = null;

			while (!isKilled()) {
				try{
					receiveSocket.receive(packet);

				} catch(SocketTimeoutException ste){
					if(this.isUpstreamDone()){
						if(!isFinalWait){
							isFinalWait = true;
							continue;
						}else{
							break;		
						}
					}	

					continue;
				} catch (IOException e) {
					logger.error(e.toString());
					break;
				}

				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

				NodePacket nodePacket = new NodePacket(packet.getData());


				if(reportTaksHandler == null){
					//					startedTime = System.currentTimeMillis();
					packetLostTracker = new PacketLostTracker(Integer.parseInt(this.getStream().getDataSize()),
							Integer.parseInt(this.getStream().getKiloBitRate()),
							NodePacket.MAX_PACKET_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND, nodePacket.getMessageId());
					reportTaksHandler = createAndLaunchReportTransportationRateRunnable(packetLostTracker);					
					StreamReportMessage streamReportMessage = 
							new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
													.flowId(flow.getFlowId())
													.build();
					this.sendStreamReport(streamReportMessage);
					
				}
				packetLostTracker.updatePacketLost(nodePacket.getMessageId());

				if(nodePacket.isLast()){
					super.setUpstreamDone();
					break;
				}
			}	

			/*
			 * The reportTask might be null when the NodeRunnable thread is 
			 * killed before enters the while loop.
			 * 
			 */
			if(reportTaksHandler != null){

				reportTaksHandler.kill();

				/*
				 * Wait for reportTask completely finished.
				 */

				while (!reportTaksHandler.isDone());
			}

			/*
			 * No mater what final state is, the NodeRunnable should always
			 * report to Master that it is going to end.
			 * 
			 */
			StreamReportMessage streamReportMessage = 
					new StreamReportMessage.Builder(EventType.RECEIVE_END, this.getUpStreamId())
			.flowId(flow.getFlowId())
			.totalBytesTransferred(this.getTotalBytesTranfered())
			.build();
			this.sendStreamReport(streamReportMessage);

			packetLostTracker.updatePacketLostForLastTime();

			if (isUpstreamDone()) { //Simulation completes as informed by upstream.

				clean();

				if (ClusterConfig.DEBUG) {

					System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has finished.");

				}
			} else if (isReset()) {

				clean();

				if (ClusterConfig.DEBUG) {

					System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has been reset.");

				}

			} else {
				if (ClusterConfig.DEBUG) {

					System.out.println("[DEBUG]SinkNode.SinkRunnable.run():" + " This thread has been killed.");

				}
			}
			if (ClusterConfig.DEBUG) {
				if (isKilled()) {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Killed.");
				} else {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Finish receiving.");
				}
			}

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

			byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 
			packet = new DatagramPacket(buf, buf.length);

			return true;
		}


		/**
		 * Create and Launch a report thread
		 * @return Future of the report thread
		 */

		private ReportTaskHandler createAndLaunchReportTransportationRateRunnable(PacketLostTracker packetLostTracker){	

			ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
			Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportTransportationRateRunnable));
			return new ReportTaskHandler(reportFuture, reportTransportationRateRunnable);
		}

		private void clean() {
			if(receiveSocket != null && !receiveSocket.isClosed()){
				receiveSocket.close();
			}

			if(streamIdToSocketMap.containsKey(getStreamId())){
				streamIdToSocketMap.remove(getStreamId());
			}

			streamIdToRunnableMap.remove(getStreamId());
		}

		private class ReportTaskHandler {

			Future<?> reportFuture;
			ReportRateRunnable reportRunnable;

			public ReportTaskHandler(Future<?> future, ReportRateRunnable runnable) {
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
		private Future<?> streamFuture;
		private ReceiveRunnable streamTask;

		public StreamTaskHandler(Future<?> streamFuture, ReceiveRunnable streamTask) {
			this.streamFuture = streamFuture;
			this.streamTask = streamTask;
		}

		public void kill() {
			streamTask.kill();
		}

		public boolean isDone() {
			return streamFuture.isDone();
		}

		public boolean isCanclled() {
			return streamFuture.isCancelled();
		}

		public void reset() {
			streamTask.reset();
		}

		public void clean() {
			streamTask.clean();
		}

		public String getStreamId() {
			return streamTask.getStreamId();
		}
	}
}
