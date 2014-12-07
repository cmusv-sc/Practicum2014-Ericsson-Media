package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.PacketLostTracker;

/**
 * A runnable to do logic for ProcessNode.
 * <p>Each stream is received in a separate WarpPoolThread.
 * After receiving all packets from the source, this thread 
 * reports the total time and total number of bytes received by the 
 * sink node back to the master using the message bus.
 * 
 * As a private class, it can only be accessed within parent class
 * @param streamId The streamId is bind to a socket and stored in the map
 * @param msgBus The message bus used to report to the master
 * 
 */
class ProcessRunnable extends NodeRunnable {

	private int totalData;
	private DatagramSocket receiveSocket;

	private long processingLoop;
	private int processingMemory;
	private InetAddress dstAddress;
	private int dstPort;
	private DatagramSocket sendSocket;
	private int rate;
	private DatagramPacket packet;
	private NodeRunnableCleaner cleaner;

	/**
	 * Constructs a ProcessRunnable.
	 * @param stream
	 * @param totalData total data that the node is supposed to receive and send
	 * @param destAddress Internet address of the destination
	 * @param dstPort port number of the destination
	 * @param processingLoop number of passes of iteration to process a packet
	 * @param processingMemory amount of bytes in memory that is used to process a packet
	 * @param rate expected transfer rate for packets
	 * @param msgBusClient messageBusClient to report to the management layer
	 * @param nodeId node id that the runnable will work for
	 * @param cleaner a cleaner to release resources
	 * @param receiveSocket a Datagram socket that is used to receive packets in the runnable
	 */
	public ProcessRunnable(Stream stream, int totalData, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory, int rate, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner, DatagramSocket receiveSocket) {

		super(stream, msgBusClient, nodeId, cleaner);

		this.totalData = totalData;
		this.dstAddress = destAddress;
		this.dstPort = dstPort;
		this.processingLoop = processingLoop;
		this.processingMemory = processingMemory;
		this.rate = rate;
		this.cleaner = cleaner;
		this.receiveSocket = receiveSocket;
		
	}

	/**
	 * Starts the runnable.
	 */
	@Override
	public void run() {

		PacketLostTracker packetLostTracker = null;
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

			setTotalBytesTranfered(this.getTotalBytesTranfered() + nodePacket.size());

			/*
			 * If reportTaskHandler is null, the packet is the first packet 
			 * received.
			 */
			if(reportTask == null) {
				packetLostTracker = new PacketLostTracker(totalData, rate, NodePacket.MAX_PACKET_LENGTH, MAX_WAITING_TIME_IN_MILLISECOND,nodePacket.getMessageId());
				reportTask = createAndLaunchReportRateRunnable(packetLostTracker);
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId())
								.build();
				this.sendStreamReport(streamReportMessage);
				
				
				
			}
			packetLostTracker.updatePacketLost(nodePacket.getMessageId());
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
	 * Initializes the receive and send DatagramSockets
	 * @return true if succeeds
	 * 	       false if acquiring an non-exist socket
	 * 					setting receive socket timeout encounters some exception
	 * 					initialize send socket encounters some exception 
	 */
	private boolean initializeSocketAndPacket(){

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

		byte[] buf = new byte[NodePacket.MAX_PACKET_LENGTH]; 
		packet = new DatagramPacket(buf, buf.length);

		return true;
	}

	/**
	 * Creates and Launches a reporting thread
	 * @return Future of the reporting thread
	 */

	private ReportTaskHandler createAndLaunchReportRateRunnable(PacketLostTracker packetLostTracker){
		ReportRateRunnable reportRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND, packetLostTracker);
		Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportRateRunnable));
		return new ReportTaskHandler(reportFuture, reportRateRunnable);
	}

	/**
	 * Sends the NodePacket embedded into DatagramPacket
	 * @param packet
	 * @param nodePacket
	 */
	private void sendPacket(DatagramPacket packet, NodePacket nodePacket){
		packet.setData(nodePacket.serialize());	
		packet.setAddress(dstAddress);
		packet.setPort(dstPort);
		System.out.println("[DELETE-JEREMY]ProcNode.Runnable.sendPacket(): send packet to port" + dstPort);
		try {
			sendSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets raw data from NodePacket, processes it and puts the output data back into NodePacket
	 * @param nodePacket
	 */
	private void processNodePacket(NodePacket nodePacket){
		byte[] data = nodePacket.getData();
		processByteArray(data);
		nodePacket.setData(data);
	}

	/**
	 * Simulates the processing of a byte array with some memory and cpu loop
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

	/**
	 * Cleans resources(sockets) associated with the Runnable
	 */
	public void clean() {
		if (!receiveSocket.isClosed()) {
			receiveSocket.close();
		}
		if (!sendSocket.isClosed()) {
			sendSocket.close();
		}
		
		this.cleaner.removeNodeRunnable(getStreamId());
	}

	/**
	 * Sends message to the down stream notifying the end of receiving and sending 
	 */
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
