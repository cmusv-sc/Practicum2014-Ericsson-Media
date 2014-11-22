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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.util.Utility;

public class ProcessingNode extends AbstractNode implements PortBindable{

	private Map<String, DatagramSocket> streamIdToRcvSocketMap = new HashMap<String, DatagramSocket>();

	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new HashMap<String, StreamTaskHandler>();

	public ProcessingNode() throws UnknownHostException {	
		super();
	}

	@Override
	public int bindAvailablePortToFlow(String streamId) {

		if (streamIdToRcvSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamIdToRcvSocketMap.get(streamId).getPort();
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

			streamIdToRcvSocketMap.put(streamId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}
	/**
	 * It is assumed that there will be only one downstream node for one stream
	 * even if Processing node exists in multiple flows.
	 */
	@Override
	public void executeTask(Stream stream) {

		//int flowIndex = -1;
		for(Flow flow : stream.getFlowList()){
			for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {
				//flowIndex++;
				if (nodePropertiesMap.get(Flow.NODE_ID).equals(getNodeId())) {
					/* Open a socket for receiving data from upstream node */
					int port = bindAvailablePortToFlow(flow.getStreamId());
					if(port == 0){
						//TODO, report to the management layer, we failed to bind a port to a socket
					}
					/* Get processing parameters */
					long processingLoop = Long.valueOf(nodePropertiesMap.get(Flow.PROCESSING_LOOP));
					int processingMemory = Integer.valueOf(nodePropertiesMap.get(Flow.PROCESSING_MEMORY));
					//Get up stream and down stream node ids
					//					upStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("UpstreamId"));
					//					downStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("DownstreamId"));

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
					} catch (UnknownHostException e1) {
						e1.printStackTrace();
					}
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
	 * Create a ReceiveProcessAndSendRunnable and launch it & record it in the map
	 * @param streamId
	 * @param totalData
	 * @param destAddress
	 * @param destPort
	 * @param processingLoop
	 * @param processingMemory
	 * @param rate
	 */
	public void launchProcessRunnable(Stream stream, int totalData, InetAddress destAddress, int destPort, long processingLoop, int processingMemory, int rate){

		ProcessRunnable procRunnable = 
				new ProcessRunnable(stream, totalData, destAddress, destPort, processingLoop, processingMemory, rate);
		Future procFuture = ThreadPool.executeAfter(new MDNTask(procRunnable), 0);
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(procFuture, procRunnable));

	}

	@Override
	public void terminateTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received terminate request.");
		}

		StreamTaskHandler streamTask = streamIdToRunnableMap.get(flow.getFlowId());
		if(streamTask == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		streamTask.kill();

		/* Notify the Upstream node */
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get("UpstreamUri") + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void releaseResource(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received clean resource request.");
		}

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getFlowId());
		while (!streamTaskHandler.isDone());
		streamTaskHandler.clean();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", flow);
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
			System.out.println("[DEBUG]ProcNode.cleanUp(): Stops streamRunnable:" + streamTask.getStreamId());
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

		/* For tracking packet lost */
		int expectedMaxPacketId;
		double packetNumPerSecond;
		int packetNumInAWindow;
		int lowPacketIdBoundry;
		int highPacketIdBoundry;
		int receivedPacketNumInAWindow;

		public ProcessRunnable(Stream stream, int totalData, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory, int rate) {

			super(stream, msgBusClient, getNodeId());

			this.totalData = totalData;
			this.dstAddress = destAddress;
			this.dstPort = dstPort;
			this.processingLoop = processingLoop;
			this.processingMemory = processingMemory;
			this.rate = rate;

			/* For tracking packet lost */
			expectedMaxPacketId = (int) Math.ceil(this.totalData * 1.0 / NodePacket.PACKET_MAX_LENGTH) - 1;
			packetNumPerSecond = this.rate * 1.0 / NodePacket.PACKET_MAX_LENGTH;
			packetNumInAWindow = (int) Math.ceil(packetNumPerSecond * MAX_WAITING_TIME_IN_MILLISECOND / 1000);
			lowPacketIdBoundry = 0;
			highPacketIdBoundry = Math.min(packetNumInAWindow - 1, expectedMaxPacketId);
			receivedPacketNumInAWindow = 0;

		}

		@Override
		public void run() {
			
			PacketLostTracker packetLostTracker = new PacketLostTracker(totalData, rate, NodePacket.PACKET_MAX_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND);

			if(!initializeSocketAndPacket()){
				return;
			}

			boolean isStarted = false;

			boolean isFinalWait = false;

			TaskHandler reportTask = null;

			while (!isKilled()) {
				try {
					receiveSocket.receive(packet);
				} catch(SocketTimeoutException ste){
					//ste.printStackTrace();
					if(this.isUpStreamDone()){
						if(!isFinalWait){
							isFinalWait = true;
							continue;
						}else{
							packetLostTracker.updatePacketLostForTimeout();
							//setLostPacketNum(getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (expectedMaxPacketId - highPacketIdBoundry));
							break;		
						}
					}					
				} catch (IOException e) {
					e.printStackTrace();
					break;
				} 

				NodePacket nodePacket = new NodePacket(packet.getData());

				int packetId = nodePacket.getMessageId();
				
				packetLostTracker.updatePacketLost(packetId);
/*				NewArrivedPacketStatus newArrivedPacketStatus = getNewArrivedPacketStatus(lowPacketIdBoundry, highPacketIdBoundry, packetId);
				if(newArrivedPacketStatus == NewArrivedPacketStatus.BEHIND_WINDOW){
					continue;
				} else{
					updatePacketLostBasedOnStatus(newArrivedPacketStatus, packetId);
				}*/

				setTotalBytesTranfered(this.getTotalBytesTranfered() + nodePacket.size());

				if(!isStarted) {
					reportTask = createAndLaunchReportRateRunnable(packetLostTracker);
					report(System.currentTimeMillis(), this.getUpStreamId(),EventType.RECEIVE_START);
					isStarted = true;
				}
				
				processNodePacket(nodePacket);

				sendPacket(packet, nodePacket);
				
				if(nodePacket.isLast()){
					break;
				}
			}	

			if(reportTask != null){
				System.out.println("Processing Node: Cancelling Future");				
				reportTask.kill();
			}	
			clean();

			report(System.currentTimeMillis(), this.getDownStreamIds().iterator().next(), EventType.SEND_END);
			this.sendEndMessageToDownstream();

			if (ClusterConfig.DEBUG) {
				if(isKilled()){
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): " + "Processing node has been killed (not finished yet)." );
				} else{
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): " + "Processing node has finished simulation." );
				}
			}
			stop();
		}

		/**
		 * Update the packet lost
		 * @param status & packetId
		 * @return 
		 */
		private void updatePacketLostBasedOnStatus(NewArrivedPacketStatus newArrivedPacketStatus, int packetId){
			switch(newArrivedPacketStatus){
			case BEHIND_WINDOW:
				return;
			case IN_WINDOW:
				receivedPacketNumInAWindow++;
				break;
			case BEYOND_WINDOW:
				setLostPacketNum(this.getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (packetId - highPacketIdBoundry - 1));
				lowPacketIdBoundry = packetId;
				highPacketIdBoundry = Math.min(lowPacketIdBoundry + packetNumInAWindow - 1, expectedMaxPacketId);
				receivedPacketNumInAWindow = 1;
				break;
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
			if ((receiveSocket = streamIdToRcvSocketMap.get(getStreamId())) == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] ProcNode.ReceiveProcessAndSendThread.initializeSockets():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return false;
			}

			try {
				receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
			} catch (SocketException e1) {
				e1.printStackTrace();
				return false;
			} 

			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException se) {
				se.printStackTrace();
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
		private TaskHandler createAndLaunchReportRateRunnable(PacketLostTracker packetLostTracker){

			ReportRateRunnable reportRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
			//WarpThreadPool.executeCached(reportTransportationRateRunnable);
			Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportRateRunnable), 0);
			return new TaskHandler(reportFuture, reportRateRunnable);
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
			}
		}

		private void report(long time, String destinationNodeId, EventType eventType) {

			ProcReportMessage procReportMsg = new ProcReportMessage();
			procReportMsg.setStreamId(getStreamId());
			procReportMsg.setTime(Utility.millisecondTimeToString(time));
			procReportMsg.setDestinationNodeId(destinationNodeId);	
			procReportMsg.setEventType(eventType);

			String fromPath = ProcessingNode.super.getNodeId() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", procReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}

		public void clean() {
			if (!receiveSocket.isClosed()) {
				receiveSocket.close();
			}
			if (!sendSocket.isClosed()) {
				sendSocket.close();
			}
			streamIdToRcvSocketMap.remove(getStreamId());
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

		public void clean() {
			streamTask.clean();
		}

		public String getStreamId() {
			return streamTask.getStreamId();
		}
	}

	private enum NewArrivedPacketStatus{
		BEYOND_WINDOW, IN_WINDOW, BEHIND_WINDOW; 
	}



}
