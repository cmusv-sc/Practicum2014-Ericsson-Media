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
	private long bytesToTransfer;
	private int rate;
	private DatagramPacket packet;

	/**
	 * The flow for which the Source is sending data. 
	 * Source Node may have multiple flows to which it is sending data. 
	 * But we are keeping only one reference as we just need any one flowId
	 * Used only for reporting to master
	 */
	private Flow flow;
	
	public SourceRunnable(Stream stream, InetAddress dstAddrStr, int dstPort, long bytesToTransfer, int rate, Flow flow, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner) {
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
//
//		//Target nanoseconds per packet
//		long targetNspp = bps2nspp(rate);
//		
//		//Running nanoseconds per packet
//		long runningNspp = targetNspp;
		
		
		StreamReportMessage streamReportMessage = 
				new StreamReportMessage.Builder(EventType.SEND_START, this.getDownStreamIds().iterator().next())
						.flowId(flow.getFlowId())
						.build();
		this.sendStreamReport(streamReportMessage);

		int packetId = 0;
//		System.err.println("[DELETE-JEREMY]SourceRunnable.run(): bytesToTransfer=" + bytesToTransfer);
		
		
		RateMonitor rateMonitor = new RateMonitor(rate);
		rateMonitor.start();
		
		long runningNspp = rateMonitor.updateRunningNspp(-packet.getLength());
		long expectedTime = System.nanoTime();
		
		while (bytesToTransfer > 0 && !isKilled()) {	
			

			NodePacket nodePacket = 
					bytesToTransfer <= NodePacket.MAX_PACKET_LENGTH ? new NodePacket(1, packetId, (int)bytesToTransfer) : new NodePacket(0, packetId);
			
			packet.setData(nodePacket.serialize());
			
			
			try {
				sendSocket.send(packet);
			} catch (IOException e1) {
				break;
			}
			

			bytesToTransfer -= packet.getLength();
			setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());

			expectedTime += rateMonitor.updateRunningNspp(packet.getLength());
			long cTime = System.nanoTime();
			long nanosecondsRemaining = (expectedTime - cTime);
			if (nanosecondsRemaining > 0) {
				try {
					long millisec = nanosecondsRemaining / 1000000;
					int nanosec = (int)(nanosecondsRemaining - (millisec * 1000000));
					Thread.sleep(millisec, nanosec);
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
	 * Initializes the sends socket and the DatagramPacket 
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
	
	/**
	 * Convert the rate in bits per second to nanoseconds per packet
	 * 
	 * @param rate The sending rate in unit bps
	 * 
	 * @return
	 */
	private static long bps2nspp(int rate) {
		double packetsPerSecond = rate / NodePacket.MAX_PACKET_LENGTH;
		return (long)(edu.cmu.mdnsim.nodes.AbstractNode.NANOSCONDS_PER_SECOND / packetsPerSecond);	
	}
	
	
	private class RateMonitor {
		
		private long lastSecondTimestmp;
		private long lastSecondSentBytes;

		private long targetRate;
		private long runningRate;
		
		private long offset;

		
		private long cumulativeDeficit = 0;
		private long nspp;
		
		public RateMonitor(int targetRate) {
			this.targetRate = targetRate;
			this.runningRate = this.targetRate;
		}
		
		public void start() {
			lastSecondTimestmp = System.currentTimeMillis();
			lastSecondSentBytes = 0;
			nspp = bps2nspp((int)runningRate);
		}
		
		public long updateRunningNspp(long packetLength) {
			
			lastSecondSentBytes += packetLength;
			
			long currentTimeMillis = System.currentTimeMillis();
			
			if ((currentTimeMillis - lastSecondTimestmp) > 1000) {
				
				long lastSecondRate = lastSecondSentBytes / (currentTimeMillis - lastSecondTimestmp) * 1000;				

				long cDeficit = targetRate - lastSecondRate;
				cumulativeDeficit += cDeficit;
				
				
				offset = Math.max(-targetRate, cumulativeDeficit);
				
				System.out.println(String.format("RateMonitor.updateRunningNspp(): target:%d, running:%d, observed:%d, offset:%d, cumulativeOffset:%d", targetRate, runningRate, lastSecondRate, offset, this.cumulativeDeficit));
				runningRate = targetRate + offset;
				
				lastSecondSentBytes = 0;
				lastSecondTimestmp = System.currentTimeMillis();
				nspp = bps2nspp((int)runningRate);
				
			}
			
			
			return nspp;
		}
		
	}
}
