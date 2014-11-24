package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;
import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;

public class ProcessingNode extends AbstractNode{

	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new HashMap<String, StreamTaskHandler>();

	public ProcessingNode() throws UnknownHostException {	
		super();
	}

	/**
	 * It is assumed that there will be only one downstream node for one stream
	 * even if Processing node exists in multiple flows.
	 */
	@Override
	public void executeTask(Message request, Stream stream) {

		Flow flow = stream.findFlow(this.getFlowId(request));
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		/* Open a socket for receiving data from upstream node */
		int port = this.getAvailablePort(flow.getStreamId());
		if(port == -1){
			//TODO: report to the management layer, we failed to bind a port to a socket
		}else{
			/* Get processing parameters */
			long processingLoop = Long.valueOf(nodePropertiesMap.get(Flow.PROCESSING_LOOP));
			int processingMemory = Integer.valueOf(nodePropertiesMap.get(Flow.PROCESSING_MEMORY));
			/* Get the IP:port */
			String[] addressAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
			/* Get the expected rate */
			int rate = Integer.parseInt(flow.getKiloBitRate());

			InetAddress targetAddress = null;
			try {
				targetAddress = InetAddress.getByName(addressAndPort[0]);
				int targetPort = Integer.valueOf(addressAndPort[1]);

				this.launchProcessRunnable(stream, 
						Integer.valueOf(stream.getDataSize()), targetAddress, targetPort, 
						processingLoop, processingMemory, rate);

				//Send the stream specification to upstream node
				Map<String, String> upstreamNodePropertiesMap = 
						flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
				upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, 
						super.getHostAddr().getHostAddress()+":"+port);
				try {
					msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
							nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}

			} catch (UnknownHostException e) {
				logger.error(e.toString());
			}
		}
	}

	/**
	 * Create a ReceiveProcessAndSendRunnable and launch it & record it in the map
	 * @param streamId
	 * @param totalData
	 * @param destAddress
	 * @param destPort
	 * @param processingLoop
	 * @param processingMemory
	 * @param rate
	 */
	public void launchProcessRunnable(Stream stream, int totalData, 
			InetAddress destAddress, int destPort, long processingLoop, int processingMemory, int rate){
		ProcessRunnable procRunnable = 
				new ProcessRunnable(stream, totalData, destAddress, destPort, processingLoop, processingMemory, rate);
		Future procFuture = ThreadPool.executeAfter(new MDNTask(procRunnable), 0);
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(procFuture, procRunnable));
	}

	@Override
	public void terminateTask(Flow flow) {

		StreamTaskHandler streamTask = streamIdToRunnableMap.get(flow.getStreamId());
		if(streamTask == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		streamTask.kill();

		/* Notify the Upstream node */
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			logger.error(e.toString());
		}
	}

	@Override
	public void releaseResource(Flow flow) {

		logger.debug(this.getNodeId() + " received clean resource request.");

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());
		while (!streamTaskHandler.isDone());

		streamTaskHandler.clean();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			logger.error(e.toString());
		}

	}

	@Override
	public void reset() {

		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {
			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();
			streamIdToRunnableMap.remove(streamTask.getStreamId());
		}

		msgBusClient.removeResource("/" + getNodeId());


	}
	/**
	 * 
	 * Each stream is received in a separate WarpPoolThread.
	 * After receiving all packets from the source, this thread 
	 * reports the total time and total number of bytes received by the 
	 * sink node back to the master using the message bus.
	 * 
	 * As a private class, it can only be accessed within parent class
	 * @param streamId The streamId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	private class ProcessRunnable extends NodeRunnable {

		private int totalData;
		private DatagramSocket receiveSocket;

		private long processingLoop;
		private int processingMemory;
		private InetAddress dstAddress;
		private int dstPort;
		private DatagramSocket sendSocket;
		private int rate;
		private DatagramPacket packet;

		public ProcessRunnable(Stream stream, int totalData, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory, int rate) {

			super(stream, msgBusClient, getNodeId());

			this.totalData = totalData;
			this.dstAddress = destAddress;
			this.dstPort = dstPort;
			this.processingLoop = processingLoop;
			this.processingMemory = processingMemory;
			this.rate = rate;
		}

		@Override
		public void run() {

			PacketLostTracker packetLostTracker = 
					new PacketLostTracker(totalData, rate, NodePacket.PACKET_MAX_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND,0);

			if(!initializeSocketAndPacket()){
				return;
			}

			boolean isFinalWait = false;
			boolean isStarted = false;
			ReportTaskHandler reportTask = null;

			while (!isKilled()) {
				try {
					receiveSocket.receive(packet);
				} catch(SocketTimeoutException ste){
					/*
					 * When socket doesn't receive any packet before time out,
					 * check whether Upstream has informed the NodeRunnable that
					 * it has finished.
					 */
					if(this.isUpstreamDone()){
						/*
						 * If the upstream has finished, wait for one more time
						 * out to ensure some packet in the flight.
						 */
						if(!isFinalWait){
							isFinalWait = true;
							continue;
						}else{
							break;		
						}
					} else {	
						/*
						 * If the upstream hsan't finished, continue waiting for
						 * upcoming packet.
						 */
						continue;
					}
				} catch (IOException e) {
					/*
					 * IOException forces the thread stopping.
					 */
					logger.error(e.toString());
					break;
				} 

				NodePacket nodePacket = new NodePacket(packet.getData());

				int packetId = nodePacket.getMessageId();

				packetLostTracker.updatePacketLost(packetId);

				setTotalBytesTranfered(this.getTotalBytesTranfered() + nodePacket.size());

				/*
				 * If reportTaskHandler is null, the packet is the first packet 
				 * received.
				 */
				if(reportTask == null) {
					reportTask = createAndLaunchReportRateRunnable(packetLostTracker);
					StreamReportMessage streamReportMessage = 
							new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
									.build();
					this.sendStreamReport(streamReportMessage);
				}

				processNodePacket(nodePacket);

				sendPacket(packet, nodePacket);
				if(!isStarted) {
					StreamReportMessage streamReportMessage = 
							new StreamReportMessage.Builder(EventType.SEND_START, this.getDownStreamIds().iterator().next())
									.build();
					this.sendStreamReport(streamReportMessage);
					isStarted = true;
				}
				if(nodePacket.isLast()){
					super.setUpstreamDone();
					break;
				}
			}	

			/*
			 * Calculating packet lost at the end
			 */
			packetLostTracker.updatePacketLostForLastTime();

			/*
			 * The reportTask might be null when the NodeRunnable thread is 
			 * killed before enters the while loop.
			 * 
			 */
			if(reportTask != null){				
				reportTask.kill();
				/*
				 * Wait for reportTask completely finished.
				 */
				while (!reportTask.isDone());
			}	

			/*
			 * No mater what final state is, the NodeRunnable should always
			 * report to Master that it is going to end.
			 * 
			 */
			StreamReportMessage streamReportMessage = 
					new StreamReportMessage.Builder(EventType.RECEIVE_END, this.getUpStreamId())
							.build();
			this.sendStreamReport(streamReportMessage);
			//this.sendStreamReport(EventType.SEND_END,this.getDownStreamIds().iterator().next());

			if (isUpstreamDone()) { //Simulation completes as informed by upstream.

				/*
				 * Processing Node should actively tell downstream it has sent out all
				 * data. This message should force the downstream stops the loop.
				 * 
				 */
				this.sendEndMessageToDownstream();

				clean();
				logger.debug("Process Runnbale is done for stream " + this.getStreamId());

			} else if (isReset()) { //NodeRunnable is reset by Master Node
				clean();
				logger.debug("Process Runnbale has been reset for stream " + this.getStreamId());

			} else { //NodeRunnable is killed by Master Node
				/*
				 * Do nothing
				 */
				logger.debug("Process Runnbale has been killed for stream " + this.getStreamId());
			}

		}

		/**
		 * Initialize the receive and send DatagramSockets
		 * @return true if succeed
		 * 	       false if acquiring an non-exist socket
		 * 					setting receive socket timeout encounters some exception
		 * 					initialize send socket encounters some exception 
		 */
		private boolean initializeSocketAndPacket(){
			if ((receiveSocket = streamIdToSocketMap.get(getStreamId())) == null) {
				logger.debug("ProcNode.ReceiveProcessAndSendThread.initializeSockets(): [Exception] Attempt to receive data for non existent stream");
				return false;
			}

			try {
				receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
			} catch (SocketException se) {
				logger.error( se.toString());
				return false;
			} 

			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException se) {
				logger.error(se.toString());
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

		private ReportTaskHandler createAndLaunchReportRateRunnable(PacketLostTracker packetLostTracker){
			ReportRateRunnable reportRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
			Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportRateRunnable), 0);
			return new ReportTaskHandler(reportFuture, reportRateRunnable);
		}

		/**
		 * Send the NodePacket embedded into DatagramPacket
		 * @param packet
		 * @param nodePacket
		 */
		private void sendPacket(DatagramPacket packet, NodePacket nodePacket){
			packet.setData(nodePacket.serialize());	
			packet.setAddress(dstAddress);
			packet.setPort(dstPort);					
			try {
				sendSocket.send(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Get raw data from NodePacket, process it and put the output data back into NodePacket
		 * @param nodePacket
		 */
		private void processNodePacket(NodePacket nodePacket){
			byte[] data = nodePacket.getData();
			processByteArray(data);
			nodePacket.setData(data);
		}

		/**
		 * Simulate the processing of a byte array with some memory and cpu loop
		 * @param data
		 */
		private void processByteArray(byte[] data){
			byte[] array = new byte[processingMemory];
			double value = 0;
			for ( int i = 0; i< processingLoop; i++) {
				value += Math.random();
				array[0] = (byte) value;
			}
		}

		public void clean() {
			if (!receiveSocket.isClosed()) {
				receiveSocket.close();
			}
			if (!sendSocket.isClosed()) {
				sendSocket.close();
			}
			streamIdToSocketMap.remove(getStreamId());
		}


		private class ReportTaskHandler {

			Future reportFuture;
			ReportRateRunnable reportRunnable;

			public ReportTaskHandler(Future future, ReportRateRunnable runnable) {
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


	/**
	 * For packet lost statistical information:
	 * When a new packet is received, there are three status:
	 * - BEYOND_WINDOW, this packet is with the highest id among all the received packet
	 * - IN_WINDOW, this packet is in the current window
	 * - BEHIND_WINDOW, this packet is regarded as a lost packet
	 */

	private class StreamTaskHandler {
		private Future streamFuture;
		private ProcessRunnable streamTask;

		public StreamTaskHandler(Future streamFuture, ProcessRunnable streamTask) {
			this.streamFuture = streamFuture;
			this.streamTask = streamTask;
		}

		public void kill() {
			streamTask.kill();
		}

		public boolean isDone() {
			return streamFuture.isDone();
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
