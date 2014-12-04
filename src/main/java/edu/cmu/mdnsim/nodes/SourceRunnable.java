package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;

class SourceRunnable extends NodeRunnable {

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
	public SourceRunnable(Stream stream, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate, Flow flow, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner) {
		super(stream, msgBusClient, nodeId, cleaner);
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

		double packetPerSecond = rate / NodePacket.PACKET_MAX_LENGTH;
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
					bytesToTransfer <= NodePacket.PACKET_MAX_LENGTH ? 
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

		byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH];
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
		cleaner.removeNodeRunnable(getStreamId());
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
