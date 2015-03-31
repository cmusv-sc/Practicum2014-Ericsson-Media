package edu.cmu.mdnsim.reporting;

import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.nodes.NodeRunnable;

/**
 * A runnable that reports statistical rate.
 * <p>Package private class could not be accessed by outside subclasses
 */
public class NodeReporter implements Runnable {

	private long lastRecordedTotalBytes = 0;

	
	private final NodeRunnable nodeRunnable;
	
	private final CPUUsageTracker cpuTracker;
	private final MemUsageTracker memTracker;
	private final PacketLostTracker packetLostTracker;
	private final PacketLatencyTracker packetLatencyTracker;
	
	
	
	// -1 to avoid time difference to be 0 when used as a divider
	private long lastRecordedTime = System.currentTimeMillis() - 1;

	private final long startedTime;

	private int intervalInMillisecond;

	private volatile boolean killed = false;

	NodeReporter(NodeReporterBuilder builder) {
		this.startedTime = System.currentTimeMillis();
		this.intervalInMillisecond = builder.intervalInMillisecon;
		this.nodeRunnable = builder.nodeRunnable;
		this.cpuTracker = builder.cpuTracker;
		this.memTracker = builder.memTracker;
		
		if (builder.packetLostTracker != null) {
			this.packetLostTracker = builder.packetLostTracker;
		} else {
			this.packetLostTracker = null;
		}
		
		if (builder.packetLatencyTracker != null) {
			this.packetLatencyTracker = builder.packetLatencyTracker;
		} else {
			this.packetLatencyTracker = null;
		}
	}

	/**
	 * Reports statistical rate as long as the current thread is not interrupted.
	 * Note, the packet lost in the last moment(several millisecond could be very large, since time spent is
	 * short)
	 */
	@Override
	public void run() {

		while (!killed) {
			calculateAndReport();
			try {
				Thread.sleep(intervalInMillisecond);
			} catch (InterruptedException e) {
				e.printStackTrace();
				continue;
			}
		}
		calculateAndReport();
		System.out.println("ReportRateRunnable.run(): " + this.nodeRunnable.getNodeId() + " report thread has been interrupted.");
		System.err.println("ReportRateRunnable.run(): Total recorded packet loss:" + packetLostTracker.getLostPacketNum());
		
	}

	public void kill() {
		this.killed = true;
	}

	/**
	 * Calculates the transfer rate and packet lost rate。
	 */
	private void calculateAndReport() {
		
		long currentTime = System.currentTimeMillis();
		long timeDiffInMillisecond = currentTime - lastRecordedTime;
		long totalTimeDiffInMillisecond = currentTime - startedTime;

		long localToTalBytesTransfered = this.nodeRunnable.getTotalBytesTranfered();
		long bytesDiff = localToTalBytesTransfered - lastRecordedTotalBytes;
		double transportationInstantRate = ((double)bytesDiff/ timeDiffInMillisecond) * 1000;
		double transportationAverageRate = 
				((double)localToTalBytesTransfered / totalTimeDiffInMillisecond) * 1000;
		
		
		StreamReportMessage.Builder reportMsgBuilder = 
				new StreamReportMessage.Builder(EventType.PROGRESS_REPORT, this.nodeRunnable.getUpStreamId(), String.format("%.2f", cpuTracker.getCPUUsage() * 100) + "%", memTracker.getMemUsage() + "MB");
		
		lastRecordedTotalBytes = localToTalBytesTransfered;
		lastRecordedTime = currentTime;

		double averageRateInKiloBitsPerSec = 0, currentRateInKiloBitsPerSec = 0;
		
		if (startedTime != 0) {
			averageRateInKiloBitsPerSec = transportationAverageRate / 128;
			currentRateInKiloBitsPerSec = transportationInstantRate / 128;

		}
		
		reportMsgBuilder.averageTransferRate(averageRateInKiloBitsPerSec)
			.currentTransferRate(currentRateInKiloBitsPerSec);
		
		if (packetLostTracker != null) {
			
			double currentPacketLossRatio = packetLostTracker.getInstantPacketLossRate();
			double averagePacketLossRatio = packetLostTracker.getAvrPacketLossRate();
			
			
			reportMsgBuilder.averagePacketLossRate(averagePacketLossRatio)
				.currentPacketLossRate(currentPacketLossRatio);
				
		}
		
		if (packetLatencyTracker != null) {
			
			reportMsgBuilder.avrEnd2EndPacketLatency(packetLatencyTracker.getAvrEnd2EndLatency())
				.avrLnk2LnkPacketLatency(packetLatencyTracker.getAvrLnk2LnkLatency());
		}
		
		this.nodeRunnable.sendStreamReport(reportMsgBuilder.build());
	}
	
	public static class NodeReporterBuilder {
		
		private int intervalInMillisecon;
		private CPUUsageTracker cpuTracker;
		private MemUsageTracker memTracker;
		private NodeRunnable nodeRunnable;
		
		private PacketLostTracker packetLostTracker;
		private PacketLatencyTracker packetLatencyTracker;
		
		public NodeReporterBuilder(int intervalInMillisecond, NodeRunnable nodeRunnable, CPUUsageTracker cpuTracker, MemUsageTracker memTracker) {
			this.intervalInMillisecon = intervalInMillisecond;
			this.cpuTracker = cpuTracker;
			this.memTracker = memTracker;
			this.nodeRunnable = nodeRunnable;
		}
		
		public NodeReporterBuilder packetLostTracker(PacketLostTracker packetLostTracker) {
			this.packetLostTracker = packetLostTracker;
			return this;
		}
		
		
		public NodeReporterBuilder packetLatencyTracker(PacketLatencyTracker tracker) {
			packetLatencyTracker = tracker;
			return this;
		}
		
		public NodeReporter build() {
			return new NodeReporter(this);
		}
		
	}
}

