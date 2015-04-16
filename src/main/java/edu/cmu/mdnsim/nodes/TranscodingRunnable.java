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
import edu.cmu.mdnsim.reporting.CPUUsageTracker;
import edu.cmu.mdnsim.reporting.MemUsageTracker;
import edu.cmu.mdnsim.reporting.NodeReporter;
import edu.cmu.mdnsim.reporting.NodeReporter.NodeReporterBuilder;
import edu.cmu.mdnsim.reporting.PacketLatencyTracker;
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
class TranscodingRunnable extends NodeRunnable {






	private DatagramSocket receiveSocket;

	private InetAddress downstreamIP;
	private int downstreamPort;
	

	private DatagramPacket packet;
	private DatagramPacket sndDatagrapm;
	private NodeRunnableCleaner cleaner;
	
	private NodePacket sndPacket;

	/**
	 * Constructor of TranscodingRunnable
	 * 
	 * @param stream
	 * @param totalData total data that the node is supposed to receive and send
	 * @param destAddress Internet address of the destination
	 * @param downstreamPort port number of the destination
	 * @param processingLoop number of passes of iteration to process a packet
	 * @param processingMemory amount of bytes in memory that is used to process a packet
	 * @param rate expected transfer rate for packets
	 * @param msgBusClient messageBusClient to report to the management layer
	 * @param nodeId node id that the runnable will work for
	 * @param cleaner a cleaner to release resources
	 * @param receiveSocket a Datagram socket that is used to receive packets in the runnable
	 */
	TranscodingRunnable(Stream stream, long totalData, InetAddress downStreamIP, int downStreamPort, 
			MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner, DatagramSocket receiveSocket,
			double adaptiveFactor) {
		
		super(stream, msgBusClient, nodeId, cleaner);

		this.downstreamIP	=	downStreamIP;
		this.downstreamPort	=	downStreamPort;

		this.cleaner = cleaner;
		this.receiveSocket = receiveSocket;
		
		
		packet = new DatagramPacket(new byte[NodePacket.MAX_PACKET_LENGTH], NodePacket.MAX_PACKET_LENGTH);
		int len = (int)(NodePacket.MAX_PACKET_LENGTH * adaptiveFactor);
		sndPacket = new NodePacket(0, 0, len);
		sndDatagrapm = new DatagramPacket(new byte[len], len);
		
	}

	/**
	 * Starts the runnable.
	 */
	@Override
	public void run() {
		
		PacketLostTracker packetLostTracker = null;
		PacketLatencyTracker packetLatencyTracker = null;
		
		
		try {
			receiveSocket.setSoTimeout(TIMEOUT_FOR_PACKET_LOSS * 1000);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		boolean isFinalWait = false;
		
		ReportTaskHandler reportTask = null;

		while (!isKilled()) {
			try {
				receiveSocket.receive(packet);
			} catch(SocketTimeoutException ste){
				logger.warn("TranscodingRunnable.run(): catch exception[" + this.getStreamId() + "]: " + ste.toString());
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
				logger.warn(e.toString());
				break;
			} 

			NodePacket nodePacket = new NodePacket(packet.getData());
			
			setTotalBytesTranfered(this.getTotalBytesTranfered() + nodePacket.size());

			/*
			 * If reportTaskHandler is null, the packet is the first packet received and NodeReporter should be configured.
			 */
			if(reportTask == null) {
								
				/* Initialize applicable trackers */
				CPUUsageTracker cpuTracker = new CPUUsageTracker();
				
				MemUsageTracker memTracker = new MemUsageTracker();
				
				int windowSize = PacketLostTracker.calculateWindowSize(Integer.parseInt(getStream().getKiloBitRate()), 
						TIMEOUT_FOR_PACKET_LOSS, NodePacket.MAX_PACKET_LENGTH);
				packetLostTracker = new PacketLostTracker(windowSize);
				
				packetLatencyTracker = new PacketLatencyTracker();
				
				NodeReporter reportThread = new NodeReporterBuilder(INTERVAL_IN_MILLISECOND, this, cpuTracker, memTracker)
					.packetLostTracker(packetLostTracker).packetLatencyTracker(packetLatencyTracker).build();
				
				Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportThread));
				reportTask = new ReportTaskHandler(reportFuture, reportThread);
				
				StreamReportMessage streamReportMessage = new StreamReportMessage.Builder(EventType.RECEIVE_START, this.getUpStreamId(), "N/A", "N/A").build();
				
				streamReportMessage.from(this.getNodeId());
				
				this.sendStreamReport(streamReportMessage);
				
			}
			
			packetLostTracker.updatePacketLost(nodePacket.getMessageId());
			packetLatencyTracker.newPacket(nodePacket);
			
			
			sendPacket(packet, nodePacket);

			if(nodePacket.isLast()){
				super.setUpstreamDone();
				break;
			}
			
			
		}	

		/*
		 * Calculating packet lost at the end
		 */
		

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
				new StreamReportMessage.Builder(EventType.RECEIVE_END, this.getUpStreamId(), "N/A", "N/A")
						.build();
		streamReportMessage.from(this.getNodeId());
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
			logger.debug("Transcoding Runnbale is done for stream " + this.getStreamId());

		} else if (isReset()) { //NodeRunnable is reset by Master Node
			clean();
			logger.debug("Transcoding Runnbale has been reset for stream " + this.getStreamId());

		} else { //NodeRunnable is killed by Master Node
			/*
			 * Do nothing
			 */
			logger.debug("Transcoding Runnbale has been killed for stream " + this.getStreamId());
		}
		
	}


	/**
	 * Sends the NodePacket embedded into DatagramPacket
	 * @param packet
	 * @param nodePacket
	 */
	private void sendPacket(DatagramPacket packet, NodePacket nodePacket){
		
		sndPacket.setFlag(nodePacket.getFlag());
		sndPacket.setMessageId(nodePacket.getMessageId());
		sndPacket.setTransmitTime(nodePacket.getTransmitTime());
		sndPacket.setForwardTime();
		//sndPacket.setData(nodePacket.get(data));
		sndDatagrapm.setData(sndPacket.serialize());
		sndDatagrapm.setAddress(downstreamIP);
		sndDatagrapm.setPort(downstreamPort);
		try {
			receiveSocket.send(sndDatagrapm);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	/**
	 * Cleans resources(sockets) associated with the Runnable
	 */
	public void clean() {
		if (!receiveSocket.isClosed()) {
			receiveSocket.close();
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
