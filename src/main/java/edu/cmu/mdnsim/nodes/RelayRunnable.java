package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.PacketLostTracker;

class RelayRunnable extends NodeRunnable {
	

	
	private DatagramSocket receiveSocket;
	private DatagramPacket receivedPacket;
	private Map<String,InetSocketAddress> downStreamUriToReceiveSocketAddress;
	private DatagramChannel sendingChannel;
	private DatagramSocket sendSocket;
	
	
	/**
	 * Constructs a new NodeRunnable object with given stream 
	 * @param stream the stream that the runnable is processing
	 * @param downStreamUri  URI of the down stream node
	 * @param destAddress destination Internet address to send packets
	 * @param destPort destination port number to send packets
	 */
	public RelayRunnable(Stream stream, String downStreamUri, InetAddress destAddress, int destPort, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner, DatagramSocket receiveSocket) {
		super(stream, msgBusClient, nodeId, cleaner);
		downStreamUriToReceiveSocketAddress  = new ConcurrentHashMap<String,InetSocketAddress>();
		downStreamUriToReceiveSocketAddress.put(downStreamUri,
				new InetSocketAddress(destAddress, destPort));
		this.receiveSocket = receiveSocket;
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
		PacketLostTracker packetLostTracker = null;
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
			

			if(reportTaskHandler == null) {
				packetLostTracker = new PacketLostTracker(Integer.parseInt(this.getStream().getDataSize()),
						Integer.parseInt(this.getStream().getKiloBitRate()),
						NodePacket.MAX_PACKET_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND, nodePacket.getMessageId());
				ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
				Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportTransportationRateRunnable));
				reportTaskHandler = new ReportTaskHandler(reportFuture, reportTransportationRateRunnable);
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
				.build();
				this.sendStreamReport(streamReportMessage);
				
				
			}

			packetLostTracker.updatePacketLost(nodePacket.getMessageId());

			//Send data to all destination nodes
			DatagramPacket packet ;
			for(InetSocketAddress destination : downStreamUriToReceiveSocketAddress.values()){
				byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 

				packet = new DatagramPacket(buf, buf.length);
				packet.setData(nodePacket.serialize());	
				packet.setAddress(destination.getAddress());
				packet.setPort(destination.getPort());
				try {
					sendSocket.send(packet);
				} catch (IOException e) {
					e.printStackTrace();
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

	/**
	 * Notifies the down stream that the processing at this node has finished.
	 */
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

	/**
	 * Cleans up sockets.
	 */
	public void clean() {
		if (!receiveSocket.isClosed()) {
			receiveSocket.close();
		}
		try {
			sendingChannel.close();
		} catch (IOException e) {

		}

		cleaner.removeNodeRunnable(getStreamId());
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
	 * Initializes the receive DatagramSocket
	 * @return true if succeed
	 * 	       false if acquiring an non-exist socket
	 * 					setting receive socket timeout encounters some exception
	 * 					initialize send socket encounters some exception 
	 */
	private boolean initializeSocketAndPacket(){

		try {
			receiveSocket.setSoTimeout(MAX_WAITING_TIME_IN_MILLISECOND);
		} catch (SocketException e1) {
			e1.printStackTrace();
			return false;
		} 

		byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 
		receivedPacket = new DatagramPacket(buf, buf.length);

		try {
			sendingChannel = DatagramChannel.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			sendSocket = new DatagramSocket();
		} catch (SocketException se) {
			logger.error(se.toString());
			return false;
		}
		return true;
	}
}
