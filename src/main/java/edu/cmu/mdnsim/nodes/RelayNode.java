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
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.mdnsim.nodes.NodeRunnable.ReportRateRunnable;
import edu.cmu.util.Utility;

/**
 *
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
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.getStream().mergeFlow(flow);
				//A new downstream node is connected to relay, just add it to existing runnable
				streamIdToRunnableMap.get(stream.getStreamId()).streamTask.addNewDestination(downStreamUri, destAddress, destPort);
			}else{
				//For the first time create a new Runnable and send stream spec to upstream node

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
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}


	}

	@Override
	public synchronized void terminateTask(Flow flow) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]RelayNode.terminateTask(): Received terminate request.");
		}

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getFlowId());
		if(streamTaskHandler == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		if(streamTaskHandler.streamTask.getDownStreamCount() == 1){
			streamTaskHandler.kill();
			/* Notify the Upstream node */
			Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
			try {
				msgBusClient.send("/tasks", nodeMap.get(Flow.UPSTREAM_URI) + "/tasks", "POST", flow);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}	
		}else{
			streamTaskHandler.streamTask.removeDownStream(
					flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI));
			//Send release resource command to downstream node 
			try {
				msgBusClient.send("/tasks", 
						flow.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void releaseResource(Flow flow) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]RelayNode.releaseResource(): Received clean resource request.");
		}

		StreamTaskHandler streamTaskHandler = streamIdToRunnableMap.get(flow.getFlowId());
		while (!streamTaskHandler.isDone());

		streamTaskHandler.clean();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void reset() {
		for (StreamTaskHandler streamTask : streamIdToRunnableMap.values()) {
			streamTask.reset();
			while(!streamTask.isDone());
			streamTask.clean();
			streamIdToRunnableMap.remove(streamTask.getStreamId());
			System.out.println("[DEBUG]RelayNode.cleanUp(): Stops streamRunnable:" + streamTask.getStreamId());
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
			boolean isStarted = false;
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
							//setLostPacketNum(this.getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (expectedMaxPacketId - highPacketIdBoundry));
							break;		
						}
					} else {
						continue;
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				} 

				if(reportTaskHandler == null) {
					ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND);
					Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
					reportTaskHandler = new ReportTaskHandler(reportFuture, reportTransportationRateRunnable);
					
					report(System.currentTimeMillis(), this.getUpStreamId(),EventType.RECEIVE_START);
				}

				NodePacket nodePacket = new NodePacket(receivedPacket.getData());

				//				int packetId = nodePacket.getMessageId();
				//				NewArrivedPacketStatus newArrivedPacketStatus = getNewArrivedPacketStatus(lowPacketIdBoundry, highPacketIdBoundry, packetId);
				//				if(newArrivedPacketStatus == NewArrivedPacketStatus.BEHIND_WINDOW){
				//					continue;
				//				} else{
				//					updatePacketLostBasedOnStatus(newArrivedPacketStatus, packetId);
				//				}

				//setTotalBytesTranfered(this.getTotalBytesTranfered() + nodePacket.size());

				//Send data to all destination nodes
				ByteBuffer buf = ByteBuffer.allocate(nodePacket.serialize().length);				
				for(InetSocketAddress destination : downStreamUriToReceiveSocketAddress.values()){
					try {
						buf.clear();
						buf.put(nodePacket.serialize());
						buf.flip();
						int bytesSent = sendingChannel.send(buf, destination);
						logger.debug("[Relay] Sent " + bytesSent + " bytes to destination " + destination.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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
			for(String downStreamId : this.getDownStreamIds()){
				report(System.currentTimeMillis(), downStreamId, EventType.SEND_END);
			}
			
			if (isUpstreamDone()) { //Simulation completes
				/*
				 * Processing node should actively tell downstream its has sent out all
				 * data. This message should force the downstream stops the loop.
				 * 
				 */
				sendEndMessageToDownstream();
				
				/*
				 * Release resources
				 */
				clean();
				
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG]ProcNode.ProcRunnable.run():" + " This thread has finished.");
				}
				
			} else if (isReset()) { //NodeRunnable is reset by Node
				/*
				 * Release resources
				 */
				clean();
				
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG]ProcNode.ProcRunnable.run():" + " This thread has been reset.");
				}
				
			} else {
				//Do nothing
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG]ProcNode.ProcRunnable.run():" + " This thread has killed.");
				}
			}

		}

		@Override
		protected void sendEndMessageToDownstream() {
			for(String downStreamURI : this.getDownStreamURIs()){
				logger.debug("[RELAY] downStreamURI - " + downStreamURI);
				try {
					msgBusClient.send(getFromPath(), downStreamURI + "/" + this.getStreamId(), 
							"DELETE", this.getStream());
				} catch (MessageBusException e) {
					e.printStackTrace();
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
				e.printStackTrace();
			}
			streamIdToSocketMap.remove(getStreamId());
		}
		/**
		 * 
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
		private void report(long time, String destinationNodeId, EventType eventType) {

			ProcReportMessage procReportMsg = new ProcReportMessage();
			procReportMsg.setStreamId(getStreamId());
			procReportMsg.setTime(Utility.millisecondTimeToString(time));
			procReportMsg.setDestinationNodeId(destinationNodeId);	
			procReportMsg.setEventType(eventType);

			String fromPath = RelayNode.super.getNodeId() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/relay_report", "POST", procReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
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
