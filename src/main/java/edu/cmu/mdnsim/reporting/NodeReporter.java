package edu.cmu.mdnsim.reporting;

import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.nodes.NodeRunnable;

/**
 * A runnable that reports statistical rate.
 * <p>Package private class could not be accessed by outside subclasses
 */
public class NodeReporter implements Runnable {

	private long lastRecordedHighestPacketId = 0;
	private long lastRecordedTotalBytes = 0;

	private long lastRecordedPacketLost = 0;
	
	private final NodeRunnable nodeRunnable;
	
	private final CPUUsageTracker cpuTracker;
	private final MemUsageTracker memTracker;
	private final PacketLostTracker packetLostTracker;
	
	
	
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
		
		if (packetLostTracker != null) {
			long localPacketLostNum = packetLostTracker.getLostPacketNum();
			long lostDiff = localPacketLostNum - lastRecordedPacketLost;
			long localHighestPacketId = packetLostTracker.getHighestPacketId();
			long packetNumDiff = localHighestPacketId - lastRecordedHighestPacketId ;
			double currentPacketLossRatio = packetNumDiff==0 ? 0 : (lostDiff * 1.0 / packetNumDiff);
			double averagePacketLossRatio = packetLostTracker.getLostPacketNum() * 1.0 / packetLostTracker.getHighestPacketId();
			lastRecordedTotalBytes = localToTalBytesTransfered;
			lastRecordedTime = currentTime;
			lastRecordedPacketLost = localPacketLostNum;
			lastRecordedHighestPacketId = localHighestPacketId;
			if (startedTime != 0) {
				double averageRateInKiloBitsPerSec = transportationAverageRate / 128;
				double currentRateInKiloBitsPerSec = transportationInstantRate / 128;
				StreamReportMessage streamReportMessage = 
						new StreamReportMessage.Builder(EventType.PROGRESS_REPORT, this.nodeRunnable.getUpStreamId())
				.averagePacketLossRate(averagePacketLossRatio)
				.averageTransferRate(averageRateInKiloBitsPerSec)
				.currentPacketLossRate(currentPacketLossRatio)
				.currentTransferRate(currentRateInKiloBitsPerSec)
				.build();
				
				this.nodeRunnable.sendStreamReport(streamReportMessage);
			}
		}
	}
	
	public static class NodeReporterBuilder {
		
		private int intervalInMillisecon;
		private CPUUsageTracker cpuTracker;
		private MemUsageTracker memTracker;
		private NodeRunnable nodeRunnable;
		
		private PacketLostTracker packetLostTracker;
		
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
		
		public NodeReporter build() {
			return new NodeReporter(this);
		}
		
	}
}

