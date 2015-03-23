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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.CPUUsageTracker;
import edu.cmu.mdnsim.reporting.MemUsageTracker;
import edu.cmu.mdnsim.reporting.NodeReporter;
import edu.cmu.mdnsim.reporting.NodeReporter.NodeReporterBuilder;
import edu.cmu.mdnsim.reporting.PacketLostTracker;

class RelayRunnable extends NodeRunnable {
	
	private static Random randomGen = new Random();
	
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
		
		//TODO: remove the dropped packet counter;
		int highestPacketId = 0;
		
		if(!initializeSocketAndPacket()){
			return;
		}		
		PacketLostTracker packetLostTracker = null;
		boolean isFinalWait = false;
		ReportTaskHandler reportTaskHandler = null;
		while (!isKilled()) {
			try {
				receiveSocket.receive(receivedPacket);
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
				int windowSize = Integer.parseInt(this.getStream().getKiloBitRate())  * 1000 * TIMEOUT_FOR_PACKET_LOSS / NodePacket.MAX_PACKET_LENGTH / 8;	
				System.out.println("RelayRunnable.run(): windowSize=" + windowSize);
				
				
				CPUUsageTracker cpuTracker = new CPUUsageTracker();
				MemUsageTracker memTracker = new MemUsageTracker();
				packetLostTracker = new PacketLostTracker(windowSize);
				
				
				NodeReporter reportThread = new NodeReporterBuilder(INTERVAL_IN_MILLISECOND, this, cpuTracker, memTracker).packetLostTracker(packetLostTracker).build();
				Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportThread));
				reportTaskHandler = new ReportTaskHandler(reportFuture, reportThread);
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
				.build();
				streamReportMessage.from(this.getNodeId());
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
				
				highestPacketId = highestPacketId < nodePacket.getMessageId() ? nodePacket.getMessageId() : highestPacketId;
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
		streamReportMessage.from(this.getNodeId());
		this.sendStreamReport(streamReportMessage);
		for(String downStreamId : this.getDownStreamIds()){
			streamReportMessage = 
					new StreamReportMessage.Builder(EventType.SEND_END, downStreamId)
			.build();
			streamReportMessage.from(this.getNodeId());
			this.sendStreamReport(streamReportMessage);
		}
		
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
			receiveSocket.setSoTimeout(TIMEOUT_FOR_PACKET_LOSS * 1000);
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
