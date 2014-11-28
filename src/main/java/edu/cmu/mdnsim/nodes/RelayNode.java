package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;
import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.nodes.NodeRunnable.ReportRateRunnable;

/**
 * Relay Node can send data to multiple flows for the same stream 
 */
public class RelayNode extends AbstractNode{

	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new HashMap<String, StreamTaskHandler>();

	public RelayNode() throws UnknownHostException {
		super();
	}
	/**
	 * For the same stream, execute task might be called multiple times.
	 * But it should use only one thread to handle that 
	 */
	@Override
	public synchronized void executeTask(Message request, Stream stream) {

		Flow flow = stream.findFlow(this.getFlowId(request));
		//Get the relay node properties
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		//Open a socket for receiving data only if it is not already open
		int receivingPort  = this.getAvailablePort(flow.getStreamId());

		String[] destinationAddressAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
		InetAddress destAddress = null;
		int destPort;
		try {
			destAddress = InetAddress.getByName(destinationAddressAndPort[0]);
			destPort = Integer.valueOf(destinationAddressAndPort[1]);
			String downStreamUri = nodePropertiesMap.get(Flow.DOWNSTREAM_URI);

			if(streamIdToRunnableMap.get(stream.getStreamId()) != null){
				//Add new flow to the stream object maintained by NodeRunable
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.getStream().replaceFlow(flow);
				//A new downstream node is connected to relay, just add it to existing runnable
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.addNewDestination(downStreamUri, destAddress, destPort);
			}else{
				//For the first time, create a new Runnable and send stream spec to upstream node
				RelayRunnable relayRunnable = 
						new RelayRunnable(stream,downStreamUri, destAddress, destPort);
				Future relayFuture = ThreadPool.executeAfter(new MDNTask(relayRunnable), 0);
				streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(relayFuture, relayRunnable));

				Map<String, String> upstreamNodePropertiesMap = 
						flow.findNodeMap(nodePropertiesMap.get(Flow.UPSTREAM_ID));
				upstreamNodePropertiesMap.put(Flow.RECEIVER_IP_PORT, 
						super.getHostAddr().getHostAddress()+":"+receivingPort);
				try {
					msgBusClient.send("/" + getNodeId() + "/tasks/" + flow.getFlowId(), 
							nodePropertiesMap.get(Flow.UPSTREAM_URI)+"/tasks", "PUT", stream);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			}
		} catch (UnknownHostException e) {
			logger.error(e.toString());
		}
	}

	@Override
	public synchronized void terminateTask(Flow flow) {

		logger.debug( this.getNodeId() + " Trying to terminate flow: " +  flow.getFlowId());

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getStreamId());

		if(streamTaskHandler == null){ //terminate a task that hasn't been started. (before executeTask is executed).
			throw new TerminateTaskBeforeExecutingException();
		}

		if(streamTaskHandler.streamTask.getDownStreamCount() == 1){ 

			streamTaskHandler.kill();
			/* Notify the Upstream node */
			Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
			try {
				msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
			} catch (MessageBusException e) {
				logger.error(e.toString());
			}	
		} else {
			streamTaskHandler.streamTask.removeDownStream(
					flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI));
			//Send release resource command to downstream node 
			try {
				msgBusClient.send("/tasks", 
						flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
			} catch (MessageBusException e) {
				logger.error(e.toString());
			}

			logger.debug(this.getNodeId() + 
					String.format(" terminateTask(): Ask downstream node(%s) to release resouces.\n", 
							flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_ID)));
		}
	}

	@Override
	public void releaseResource(Flow flow) {

		logger.debug("%s [DEBUG]RelayNode.releaseResource(): try to release flow %s\n", this.getNodeId(), flow.getFlowId());
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
	public synchronized void reset() {
		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {
			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();
			logger.debug(this.getNodeId() + " [DEBUG]RelayNode.cleanUp(): Stops streamRunnable:" + streamTask.getStreamId());
		}

		msgBusClient.removeResource("/" + getNodeId());

	}
	private class RelayRunnable extends NodeRunnable {
		private DatagramSocket receiveSocket;
		private DatagramPacket receivedPacket;
		private Map<String,InetSocketAddress> downStreamUriToReceiveSocketAddress ;
		private DatagramChannel sendingChannel;
		/**
		 * Create a new NodeRunnable object with given stream 
		 * @param stream
		 * @param downStreamUri 
		 * @param destAddress
		 * @param destPort
		 */
		public RelayRunnable(Stream stream, String downStreamUri, InetAddress destAddress, int destPort) {
			super(stream, msgBusClient, getNodeId());
			downStreamUriToReceiveSocketAddress  = new ConcurrentHashMap<String,InetSocketAddress>();
			downStreamUriToReceiveSocketAddress.put(downStreamUri,
					new InetSocketAddress(destAddress, destPort));
		}

		public void removeDownStream(String downStreamUri) {
			this.downStreamUriToReceiveSocketAddress.remove(downStreamUri);
		}

		public synchronized int getDownStreamCount() {
			return this.downStreamUriToReceiveSocketAddress.size();
		}

		@Override
		public void run() {
			if(!initializeSocketAndPacket()){
				return;
			}		
			PacketLostTracker packetLostTracker = new PacketLostTracker(Integer.parseInt(this.getStream().getDataSize()),
					Integer.parseInt(this.getStream().getKiloBitRate()),
					NodePacket.PACKET_MAX_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND, 0);

			boolean isFinalWait = false;
			ReportTaskHandler reportTaskHandler = null;
			while (!isKilled()) {
				try {
					receiveSocket.receive(receivedPacket);
					logger.debug("[RELAY] Received Packet" );
				} catch(SocketTimeoutException ste){
					if(this.isUpstreamDone()){
						if(!isFinalWait){
							isFinalWait = true;
							continue;
						}else{
							break;		
						}
					} else {
						continue;
					}
				} catch (IOException e) {
					logger.error(e.toString());
					break;
				} 
				setTotalBytesTranfered(getTotalBytesTranfered() + receivedPacket.getLength());

				NodePacket nodePacket = new NodePacket(receivedPacket.getData());
				packetLostTracker.updatePacketLost(nodePacket.getMessageId());
				
				if(reportTaskHandler == null) {
					ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
					Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
					reportTaskHandler = new ReportTaskHandler(reportFuture, reportTransportationRateRunnable);
					StreamReportMessage streamReportMessage = 
							new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
									.build();
					this.sendStreamReport(streamReportMessage);
				}

				//NodePacket nodePacket = new NodePacket(receivedPacket.getData());

				//Send data to all destination nodes
				ByteBuffer buf = ByteBuffer.allocate(nodePacket.serialize().length);				
				for(InetSocketAddress destination : downStreamUriToReceiveSocketAddress.values()){
					try {
						buf.clear();
						buf.put(nodePacket.serialize());
						buf.flip();
						int bytesSent = sendingChannel.send(buf, destination);
						logger.debug(getNodeId() + " sent " + bytesSent + " bytes to destination " + destination.toString());
					} catch (IOException e) {
						logger.error(e.toString());
					}
				}
				if(nodePacket.isLast()){
					super.setUpstreamDone();
					break;
				}
			}

			/*
			 * ReportTaskHandler might be null as the thread might be killed
			 * before the while loop. The report thread is started in the while
			 * loop. Therefore, the reportTaskHandler might be null.
			 * 
			 */
			if(reportTaskHandler != null){
				reportTaskHandler.kill();

				/*
				 * Wait for report thread completes totally.
				 */
				while(!reportTaskHandler.isDone());
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
			for(String downStreamId : this.getDownStreamIds()){
				streamReportMessage = 
						new StreamReportMessage.Builder(EventType.SEND_END, downStreamId)
								.build();
				this.sendStreamReport(streamReportMessage);
			}
			packetLostTracker.updatePacketLostForLastTime();
			if (isUpstreamDone()) { //Simulation completes
				/*
				 * Processing node should actively tell downstream its has sent out all
				 * data. This message should force the downstream stops the loop.
				 * 
				 */
				sendEndMessageToDownstream();

				clean();
				logger.debug("Relay Runnbale is done for stream " + this.getStreamId());

			} else if (isReset()) { //NodeRunnable is reset by Master Node
				clean();
				logger.debug("Relay Runnbale has been reset for stream " + this.getStreamId());

			} else { //NodeRunnable is killed by Master Node
				/*
				 * Do nothing
				 */
				logger.debug("Relay Runnbale has been killed for stream " + this.getStreamId());
			}

		}

		@Override
		protected void sendEndMessageToDownstream() {
			for(String downStreamURI : this.getDownStreamURIs()){
				try {
					msgBusClient.send(getFromPath(), downStreamURI + "/" + this.getStreamId(), 
							"DELETE", this.getStream());
				} catch (MessageBusException e) {
					logger.error(e.toString());
				}
			}

		}

		public void clean() {
			if (!receiveSocket.isClosed()) {
				receiveSocket.close();
			}
			try {
				sendingChannel.close();
			} catch (IOException e) {
				
			}
			streamIdToSocketMap.remove(getStreamId());
			streamIdToRunnableMap.remove(getStreamId());
		}
		/**
		 * Adds new downstream node to relay
		 * @param downStreamUri 
		 * @param destAddress
		 * @param destPort
		 */
		public void addNewDestination(String downStreamUri, InetAddress destAddress, int destPort) {
			downStreamUriToReceiveSocketAddress.put(downStreamUri, 
					new InetSocketAddress(destAddress, destPort));
		}
		/**
		 * Initialize the receive DatagramSocket
		 * @return true if succeed
		 * 	       false if acquiring an non-exist socket
		 * 					setting receive socket timeout encounters some exception
		 * 					initialize send socket encounters some exception 
		 */
		private boolean initializeSocketAndPacket(){
			if ((receiveSocket = streamIdToSocketMap.get(getStreamId())) == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] RelayNode.RelayRunnable.initializeSocket():" 
							+ "[Exception]Attempt to receive data for non existent stream");
				}
				return false;
			}

			try {
				receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
			} catch (SocketException e1) {
				e1.printStackTrace();
				return false;
			} 

			byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH]; 
			receivedPacket = new DatagramPacket(buf, buf.length);

			try {
				sendingChannel = DatagramChannel.open();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
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
	private class StreamTaskHandler {
		private Future streamFuture;
		private RelayRunnable streamTask;

		public StreamTaskHandler(Future streamFuture, RelayRunnable streamTask) {
			this.streamFuture = streamFuture;
			this.streamTask = streamTask;
		}

		public void kill() {
			streamTask.kill();
		}

		public boolean isDone() {
			return streamFuture.isDone();
		}

		/**
		 * Reset the NodeRunnable. The NodeRunnable should be interrupted (set killed),
		 * and set reset flag as actions for clean up is different from being killed.
		 */
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
