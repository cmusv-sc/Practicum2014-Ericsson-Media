package edu.cmu.mdnsim.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.reporting.PacketLostTracker;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public abstract class NodeRunnable implements Runnable {
	
	public static final int MAX_WAITING_TIME_IN_MILLISECOND = 1000;
	
	public static final int INTERVAL_IN_MILLISECOND = 1000;
	
	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.node-runnable");
	private Stream stream;
	private AtomicInteger totalBytesTransfered = new AtomicInteger(0);
	private AtomicInteger lostPacketNum = new AtomicInteger(0);
	MessageBusClient msgBusClient;
	private String nodeId;
	NodeRunnableCleaner cleaner;

	
	
	/**
	 * Used to indicate NodeRunnable Thread to stop processing. Will be set to
	 * true when Master sends Terminate message for the flow attached to this
	 * NodeRunnable
	 */
	private boolean killed = false;

	/**
	 * Used to indicate NodeRunnable thread is reset by Node thread.
	 */
	private boolean reset = false;
	/**
	 * Used to release resources like socket after the flow is terminated.
	 */
	private boolean stopped = false;

	private volatile boolean upStreamDone = false;

	public NodeRunnable(Stream stream, MessageBusClient msgBusClient, String nodeId, NodeRunnableCleaner cleaner) {
		this.stream = stream;
		this.msgBusClient = msgBusClient;
		this.nodeId = nodeId;
		try {
			msgBusClient.addMethodListener(getResourceName(),
					"DELETE", this, "upStreamDoneSending");
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.cleaner = cleaner;
	}

	String getNodeId() {
		return nodeId;
	}

	private String getResourceName() {
		return "/" + getNodeId() + "/" + this.getStreamId();
	}

	public void upStreamDoneSending(Stream stream) {

		setUpstreamDone();
		msgBusClient.removeResource(getResourceName());
	}

	public abstract void run();

	public String getStreamId() {
		return this.stream.getStreamId();
	}

	public int getTotalBytesTranfered() {
		return totalBytesTransfered.get();
	}

	public void setTotalBytesTranfered(int totalBytesTranfered) {
		this.totalBytesTransfered.set(totalBytesTranfered);
	}

	public synchronized int getLostPacketNum() {
		return lostPacketNum.get();
	}

	public synchronized void setLostPacketNum(int lostPacketNum) {
		this.lostPacketNum.set(lostPacketNum);
	}

	public synchronized void kill() {
		killed = true;
	}

	public synchronized boolean isKilled() {
		return killed;
	}

	/**
	 * Reset the NodeRunnable. The NodeRunnable should be interrupted (set killed),
	 * and set reset flag as actions for clean up is different from being killed.
	 */
	public synchronized void reset() {
		this.killed = true;
		this.reset = true;
	}

	public synchronized boolean isReset() {
		return this.reset;
	}

	public synchronized void stop() {
		stopped = true;
	}

	public synchronized boolean isStopped() {
		return stopped;
	}

	public Stream getStream() {
		return this.stream;
	}

	public void setStream(Stream stream) {
		this.stream = stream;
	}


	abstract protected void sendEndMessageToDownstream();

	protected String getFromPath() {
		return "/" + getNodeId() + "/" + this.getStreamId();

	}

	protected void setUpstreamDone() {
		this.upStreamDone = true;
	}

	public boolean isUpstreamDone() {
		return upStreamDone;
	}

	/**
	 * Gets up stream node id for the current node
	 * @return null if not found
	 */
	protected String getUpStreamId() {
		Stream stream = NodeRunnable.this.getStream();
		for(Flow flow : stream.getFlowList()){
			Map<String, String> nodeMap = flow.findNodeMap(nodeId);
			if(nodeMap != null){
				return nodeMap.get(Flow.UPSTREAM_ID);
			}
		}
		return null;	
	}
	protected Set<String> getDownStreamIds() {
		Set<String> downStreamIds = new HashSet<String>();
		Stream stream = NodeRunnable.this.getStream();
		for(Flow flow : stream.getFlowList()){
			Map<String, String> nodeMap = flow.findNodeMap(nodeId);
			if(nodeMap != null && nodeMap.get(Flow.DOWNSTREAM_ID) != null){
				downStreamIds.add(nodeMap.get(Flow.DOWNSTREAM_ID));
			}
		}
		return downStreamIds;	
	}
	protected Set<String> getDownStreamURIs() {
		Set<String> downStreamURIs = new HashSet<String>();
		Stream stream = getStream();
		for(Flow flow : stream.getFlowList()){
			Map<String, String> nodeMap = flow.findNodeMap(nodeId);
			//Down Stream URI may be null for some nodes as there might be multiple flows in a single stream having same node 
			// but downstream uri will be updated only for one node in the stream spec
			if(nodeMap != null && nodeMap.get(Flow.DOWNSTREAM_URI) != null){
				downStreamURIs.add(nodeMap.get(Flow.DOWNSTREAM_URI));
			}
		}
		System.out.println("Down Stream Uris: " + downStreamURIs.toString());
		return downStreamURIs;	
	}
	protected void sendStreamReport(StreamReportMessage streamReportMessage) {
		String fromPath = "/" + this.getNodeId() + "/" + this.getStreamId();
		try {
			msgBusClient.sendToMaster(fromPath, "/stream_report", "POST", streamReportMessage);
		} catch (MessageBusException e) {
			e.printStackTrace();
			logger.error(e.toString());
		};
	}
	
	abstract void clean();
	
//	abstract int getDownstreamCount();
	/**
	 * Package private class could not be accessed by outside subclasses
	 * 
	 *
	 */
	protected class ReportRateRunnable implements Runnable {

		private int lastRecordedHighestPacketId = 0;
		private int lastRecordedTotalBytes = 0;

		private int lastRecordedPacketLost = 0;

		PacketLostTracker packetLostTracker;
		// -1 to avoid time difference to be 0 when used as a divider
		private long lastRecordedTime = System.currentTimeMillis() - 1;

		private final long startedTime;

		private int intervalInMillisecond;

		private volatile boolean killed = false;

		public ReportRateRunnable(int intervalInMillisecond, PacketLostTracker packetLostTracker) {
			this.intervalInMillisecond = intervalInMillisecond;
			this.packetLostTracker = packetLostTracker;
			startedTime = System.currentTimeMillis();
		}

		/**
		 * Make a while loop as long as the current thread is not interrupted
		 * Call the calculateAndReport method after the while loop to report the
		 * status for the last moment Note, the packet lost in the last
		 * moment(several millisecond could be very large, since time spent is
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
			System.out.println("ReportRateRunnable.run(): " + NodeRunnable.this.nodeId + " report thread has been interrupted.");

		}

		public void kill() {
			this.killed = true;
		}

		/**
		 * Calculate the transfer rate and packet lost rate。 Call the report
		 * methods Update the last records
		 */
		private void calculateAndReport() {
			long currentTime = System.currentTimeMillis();
			long timeDiffInMillisecond = currentTime - lastRecordedTime;
			long totalTimeDiffInMillisecond = currentTime - startedTime;

			int localToTalBytesTransfered = getTotalBytesTranfered();
			int bytesDiff = localToTalBytesTransfered - lastRecordedTotalBytes;
			double transportationInstantRate = ((double)bytesDiff/ timeDiffInMillisecond) * 1000;
			double transportationAverageRate = 
					((double)localToTalBytesTransfered / totalTimeDiffInMillisecond) * 1000;

			int localPacketLostNum = packetLostTracker.getLostPacketNum();
			int lostDiff = localPacketLostNum - lastRecordedPacketLost;
			int localHighestPacketId = packetLostTracker.getHighestPacketId();
			int packetNumDiff = localHighestPacketId - lastRecordedHighestPacketId ;
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
						new StreamReportMessage.Builder(EventType.PROGRESS_REPORT, getUpStreamId())
				.averagePacketLossRate(averagePacketLossRatio)
				.averageTransferRate(averageRateInKiloBitsPerSec)
				.currentPacketLossRate(currentPacketLossRatio)
				.currentTransferRate(currentRateInKiloBitsPerSec)
				.build();
				sendStreamReport(streamReportMessage);
			}
		}
	}
}
