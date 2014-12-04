package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;

public class SourceNode extends AbstractNode {

	private Map<String, StreamTaskHandler> streamIdToRunnableMap = new ConcurrentHashMap<String, StreamTaskHandler>();

	public SourceNode() throws UnknownHostException {
		super();
	}	
	/**
	 * Assumptions:
	 * 1. All the flows in the stream should have source node in it.
	 * 2. Properties for source node should be same in all flows.
	 * 3. It is assumed that there will be only one downstream node for one stream
	 * 		even if Source node exists in multiple flows.
	 * 
	 */
	@Override
	public void executeTask(Message request, Stream stream) {
		logger.debug(this.getNodeId() + " received a work specification. StreamId: " + stream.getStreamId());

		Flow flow = stream.findFlow(this.getFlowId(request));
		Map<String, String> nodePropertiesMap = flow.findNodeMap(getNodeId());
		String[] ipAndPort = nodePropertiesMap.get(Flow.RECEIVER_IP_PORT).split(":");
		String destAddrStr = ipAndPort[0];
		int destPort = Integer.parseInt(ipAndPort[1]);
		int dataSizeInBytes = Integer.parseInt(flow.getDataSize());
		int rateInKiloBitsPerSec = Integer.parseInt(flow.getKiloBitRate());
		int rateInBytesPerSec = rateInKiloBitsPerSec * 128;  //Assumed that KiloBits = 1024 bits

		try {
			createAndLaunchSendRunnable(stream, InetAddress.getByName(destAddrStr), destPort, 
					dataSizeInBytes, rateInBytesPerSec, flow);					
		} catch (UnknownHostException e) {
			e.printStackTrace();
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
	public void createAndLaunchSendRunnable(Stream stream, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate, Flow flow){
		SendRunnable sendRunnable = new SendRunnable(stream, destAddrStr, destPort, bytesToTransfer, rate, flow);
		Future<?> sendFuture = NodeContainer.ThreadPool.submit(new MDNTask(sendRunnable));
		streamIdToRunnableMap.put(stream.getStreamId(), new StreamTaskHandler(sendFuture, sendRunnable));
	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void terminateTask(Flow flow) {
		StreamTaskHandler sendTaskHanlder = streamIdToRunnableMap.get(flow.getStreamId());
		if(sendTaskHanlder == null){
			throw new RuntimeException("Terminate task before executing");
		}
		sendTaskHanlder.kill();
		releaseResource(flow);
	}
	/**
	 * For Source Node, stopping flow and stopping stream is same thing.
	 */
	@Override
	public void releaseResource(Flow flow) {
		StreamTaskHandler sndThread = streamIdToRunnableMap.get(flow.getStreamId());

		while (!sndThread.isDone());

		logger.debug(this.getNodeId() + " starts to clean-up resources for flow: " + flow.getFlowId());

		sndThread.clean();
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		try {
			msgBusClient.send("/tasks", nodeMap.get(Flow.DOWNSTREAM_URI) + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
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

	private class SendRunnable extends NodeRunnable {

		private DatagramSocket sendSocket = null;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private DatagramPacket packet;
		/**
		 * The flow for which the Source is sending data. 
		 * Source Node may have multiple flows to which it is sending data. 
		 * But we are keeping only one reference as we just need any one flowId
		 * Used only for reporting to master
		 */
		private Flow flow;
		public SendRunnable(Stream stream, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate, Flow flow) {
			super(stream, msgBusClient, getNodeId());
			this.dstAddrStr = dstAddrStr;
			this.dstPort = dstPort;
			this.bytesToTransfer = bytesToTransfer;
			this.rate = rate;	
			this.flow = flow;
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

			double packetPerSecond = rate / NodePacket.MAX_PACKET_LENGTH;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 
			
			StreamReportMessage streamReportMessage = 
					new StreamReportMessage.Builder(EventType.SEND_START, this.getDownStreamIds().iterator().next())
							.flowId(flow.getFlowId())
							.build();
			this.sendStreamReport(streamReportMessage);

			int packetId = 0;

			while (bytesToTransfer > 0 && !isKilled()) {	
				long begin = System.currentTimeMillis();

				NodePacket nodePacket = 
						bytesToTransfer <= NodePacket.MAX_PACKET_LENGTH ? 
								new NodePacket(1, packetId, bytesToTransfer) : new NodePacket(0, packetId);
				packet.setData(nodePacket.serialize());
				
				try {
					sendSocket.send(packet);
				} catch (IOException e1) {
					break;
				}
				

				bytesToTransfer -= packet.getLength();
				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

				long end = System.currentTimeMillis();
				long millisRemaining = millisecondPerPacket - (end - begin);
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException e) {
						logger.error(e.toString());
					}
				}
				packetId++;
			}
			/*
			 * No mater what final state is, the NodeRunnable should always
			 * report to Master that it is going to end.
			 */
			streamReportMessage = 
					new StreamReportMessage.Builder(EventType.SEND_END, this.getDownStreamIds().iterator().next())
							.flowId(flow.getFlowId())
							.build();
			this.sendStreamReport(streamReportMessage);

			if (bytesToTransfer <= 0) { //Simulation completes

				/*
				 * This message is required only for cases when last packet is lost.
				 * Source should actively tell downstream it has sent out all
				 * data. This message should force the downstream stops the loop.
				 */
				this.sendEndMessageToDownstream();

				clean();
				logger.debug("Send Runnbale is done for stream " + this.getStreamId());

			} else if (isReset()) { //NodeRunnable is reset by Master Node
				
				clean();
				logger.debug("Send Runnbale has been reset for stream " + this.getStreamId());

			} else { //NodeRunnable is killed by Master Node
				/*
				 * Do nothing
				 */
				logger.debug("Send Runnbale has been killed for stream " + this.getStreamId());
			}

		}

		/**
		 * Initialize the send socket and the DatagramPacket 
		 * @return true if successfully done
		 */
		private boolean initializeSocketAndPacket(){
			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException se) {
				logger.error(se.toString());
				return false;
			}

			byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH];
			packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);

			return true;	
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
		private Future<?> streamFuture;
		private SendRunnable streamTask;

		public StreamTaskHandler(Future<?> streamFuture, SendRunnable streamTask) {
			this.streamFuture = streamFuture;
			this.streamTask = streamTask;
		}

		/**
		 * Reset the NodeRunnable. The NodeRunnable should be interrupted (set killed),
		 * and set reset flag as actions for clean up is different from being killed.
		 */
		public void reset() {
			streamTask.reset();
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
