package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.Future;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.CPUUsageTracker;
import edu.cmu.mdnsim.reporting.MemUsageTracker;
import edu.cmu.mdnsim.reporting.NodeReporter;
import edu.cmu.mdnsim.reporting.NodeReporter.NodeReporterBuilder;

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
		
//		File logFile = new File("src -" + System.currentTimeMillis() + ".log");
//		FileOutputStream out = null;
//		try {
//			out = new FileOutputStream(logFile);
//		} catch (FileNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		StreamReportMessage streamReportMessage = 
				new StreamReportMessage.Builder(EventType.SEND_START, this.getDownStreamIds().iterator().next(), "N/A", "N/A")
						.flowId(flow.getFlowId())
						.build();
		streamReportMessage.from(this.getNodeId());
		this.sendStreamReport(streamReportMessage);

		int packetId = 0;
		
		RateMonitor rateMonitor = new RateMonitor(rate);
		rateMonitor.start();
		
		long expectedTime = System.nanoTime();
		Random ranGen = new Random();
		long droppedPktCnt = 0;
		
		CPUUsageTracker cpuTracker = new CPUUsageTracker();
		MemUsageTracker memTracker = new MemUsageTracker();
		
		//TOOD: Remever to kill this thread!
		NodeReporter reportThread = new NodeReporterBuilder(INTERVAL_IN_MILLISECOND, this, cpuTracker, memTracker).build();
		Future<?> reportFuture = NodeContainer.ThreadPool.submit(new MDNTask(reportThread));
		ReportTaskHandler reportTaskHandler = new ReportTaskHandler(reportFuture, reportThread);
		
		//TOOD: FOR DEBUG
		while (bytesToTransfer > 0 && !isKilled()) {	
			

			NodePacket nodePacket = 
					bytesToTransfer <= NodePacket.MAX_PACKET_LENGTH ? new NodePacket(1, packetId, (int)bytesToTransfer) : new NodePacket(0, packetId);
			
			packet.setData(nodePacket.serialize());
			
			try {
				if (ranGen.nextDouble() > 0.3) {
					sendSocket.send(packet);
				} else {
//					out.write(("![" + nodePacket.getMessageId() + "]\n").getBytes());
					droppedPktCnt++;
				}
				
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
			
			if (bytesToTransfer <= 0) {
				System.out.println("[SourceRunnable run] Last sent packet ID: " + packetId);
			}
			packetId++;
		}
		
		reportTaskHandler.kill();
		while(!reportTaskHandler.isDone());
		
		/*
		 * No mater what final state is, the NodeRunnable should always
		 * report to Master that it is going to end.
		 */
		streamReportMessage = 
				new StreamReportMessage.Builder(EventType.SEND_END, this.getDownStreamIds().iterator().next(), "N/A", "N/A")
						.flowId(flow.getFlowId())
						.build();
		streamReportMessage.from(this.getNodeId());
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
		
		System.out.println("SourceRunnable.run(): Total dropped packet = " + droppedPktCnt);

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
		logger.debug("SourceRunnable.initializeSocketAndPacket(): dstAddrStr:" + dstAddrStr + "#" + dstPort);
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
	
	/**
	 * 
	 * This class is used to adjust the transferring rates in the pace of seconds.
	 * Source is supposed to call the {@link #updateRunningNspp(long) updateRunningNspp}
	 * every time the source sends a packet and obtain the nanoseconds per packet.
	 * The return values keeps changing on one second basis to adjust the transfer rate to the target one.
	 * 
	 * @author Geng Fu
	 *
	 */
	private class RateMonitor {
		
		/**
		 * The starting time-stamp of current second
		 */
		private long lastSecondTimestmp;
		
		/**
		 * The total bytes sent in current second
		 */
		private long lastSecondSentBytes;

		/**
		 * The target rate(bytes per second) to achieve by rate correction
		 */
		private long targetRate;
		
		/**
		 * The computed rate (bytes per second) in runtime to compensate the deficit
		 * of target rate and actual transferring rate because of system.
		 */
		private long runningRate;

		/**
		 * The accumulative deficit. This should aim to eliminated by 
		 */
		private long cumulativeDeficit = 0;
		
		/**
		 * Nanoseconds per packet
		 */
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
				
				//The offset cannot be less than -targeRate because the runningRate should be non-negative.
				long offset = Math.max(-targetRate, cumulativeDeficit);
				
				logger.debug(String.format("RateMonitor.updateRunningNspp(): target:%d, running:%d, observed:%d, offset:%d, cumulativeOffset:%d", targetRate, runningRate, lastSecondRate, offset, this.cumulativeDeficit));
				
				//Adjust the running rate to meet the target rate on average.
				runningRate = targetRate + offset;
				
				lastSecondSentBytes = 0;
				lastSecondTimestmp = System.currentTimeMillis();
				nspp = bps2nspp((int)runningRate);
				
			}
			
			return nspp;
		}
		
	}
}
