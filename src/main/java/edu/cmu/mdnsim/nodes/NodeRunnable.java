package edu.cmu.mdnsim.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;

public abstract class NodeRunnable implements Runnable {

	private Stream stream;
	private AtomicInteger totalBytesTransfered = new AtomicInteger(0);
	private AtomicInteger lostPacketNum = new AtomicInteger(0);
	private MessageBusClient msgBusClient;
	private String nodeId;
	
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

	public NodeRunnable(Stream stream, MessageBusClient msgBusClient, String nodeId) {
		this.stream = stream;
		this.msgBusClient = msgBusClient;
		this.nodeId = nodeId;
		try {
			System.err.println("Resource Name " + this.getResourceName());
			msgBusClient.addMethodListener(getResourceName(),
					"DELETE", this, "upStreamDoneSending");
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String getNodeId() {
		return nodeId;
	}
	
	private String getResourceName() {
		return "/" + getNodeId() + "/" + this.getStreamId();
	}

	public void upStreamDoneSending(Stream stream) {

		this.upStreamDone = true;
		this.msgBusClient.removeResource(getResourceName());
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
//	protected void sendEndMessageToDownstream() {
//		try {
//			msgBusClient.send(getFromPath(), this.getFlow()
//					.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI)
//					+ "/" + this.getFlowId(), "DELETE", this.getFlow());
//		} catch (MessageBusException e) {
//			e.printStackTrace();
//		}
//	}

	protected String getFromPath() {
		return "/" + getNodeId() + "/" + this.getStreamId();

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
			if(nodeMap != null){
				downStreamIds.add(nodeMap.get(Flow.DOWNSTREAM_ID));
			}
		}
		return downStreamIds;	
	}
	protected Set<String> getDownStreamURIs() {
		Set<String> downStreamURIs = new HashSet<String>();
		Stream stream = NodeRunnable.this.getStream();
		for(Flow flow : stream.getFlowList()){
			Map<String, String> nodeMap = flow.findNodeMap(nodeId);
			if(nodeMap != null){
				downStreamURIs.add(nodeMap.get(Flow.DOWNSTREAM_URI));
			}
		}
		System.out.println("Down Stream Uris: " + downStreamURIs.toString());
		return downStreamURIs;	
	}
	/**
	 * Package private class could not be accessed by outside subclasses
	 * 
	 *
	 */
	protected class ReportRateRunnable implements Runnable {

		private int lastRecordedTotalBytes = 0;

		private int lastRecordedPacketLost = 0;

		// -1 to avoid time difference to be 0 when used as a divider
		private long lastRecordedTime = System.currentTimeMillis() - 1;

		private final long startedTime;

		private int intervalInMillisecond;

		private volatile boolean killed = false;

		public ReportRateRunnable(int intervalInMillisecond) {
			this.intervalInMillisecond = intervalInMillisecond;
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
			long transportationInstantRate = (long) (bytesDiff * 1.0
					/ timeDiffInMillisecond * 1000);
			long transportationAverageRate = (long) (localToTalBytesTransfered
					* 1.0 / totalTimeDiffInMillisecond * 1000);

			int localPacketLostNum = getLostPacketNum();
			int lostDiff = localPacketLostNum - lastRecordedPacketLost;
			long dataLostInstantRate = (long) (lostDiff
					* NodePacket.PACKET_MAX_LENGTH * 1.0
					/ timeDiffInMillisecond * 1000);
			long dateLostAverageRate = (long) (localPacketLostNum
					* NodePacket.PACKET_MAX_LENGTH * 1.0
					/ totalTimeDiffInMillisecond * 1000);

			lastRecordedTotalBytes = localToTalBytesTransfered;
			lastRecordedPacketLost = localPacketLostNum;
			lastRecordedTime = currentTime;

			if (startedTime != 0) {
					reportTransportationRate(transportationAverageRate,
							transportationInstantRate);
				reportPacketLost(dateLostAverageRate, dataLostInstantRate);
			}
		}

		private void reportPacketLost(long packetLostAverageRate,
				long dataLostInstantRate) {
			System.out.println("[Packet Lost] " + packetLostAverageRate + " "
					+ dataLostInstantRate);
		}

		private void reportTransportationRate(long averageRate, long instantRate) {
			System.out
					.println("[RATE]" + " " + averageRate + " " + instantRate);
			NodeType nodeType = getNodeType();
			System.out.println("NodeType:" + nodeType);
			String fromPath = getNodeId()
					+ "/progress-report";
			if (nodeType == NodeType.SINK) {
				SinkReportMessage msg = new SinkReportMessage();
				msg.setEventType(EventType.PROGRESS_REPORT);
				msg.setStreamId(NodeRunnable.this.getStreamId());
				msg.setDestinationNodeId(getUpStreamId());
				msg.setAverageRate("" + averageRate);
				msg.setCurrentRate("" + instantRate);
				try {
					msgBusClient.sendToMaster(fromPath,
							"/sink_report", "POST", msg);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			} else if (nodeType == NodeType.PROC) {
				ProcReportMessage msg = new ProcReportMessage();
				msg.setEventType(EventType.PROGRESS_REPORT);
				msg.setStreamId(NodeRunnable.this.getStreamId());
				msg.setDestinationNodeId(getUpStreamId());
				msg.setAverageRate("" + averageRate);
				msg.setCurrentRate("" + instantRate);
				try {
					msgBusClient.sendToMaster(fromPath,
							"/processing_report", "POST", msg);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			}
		}

		// TODO: Add Node Type instead of using the parser
		private NodeType getNodeType() {
			String nodeTypeStr = getNodeId().split(":")[1];
			nodeTypeStr = nodeTypeStr.substring(0, nodeTypeStr.length() - 1);
			if (nodeTypeStr.toLowerCase().equals("sink")) {
				return NodeType.SINK;
			} else if (nodeTypeStr.toLowerCase().equals("processing")) {
				return NodeType.PROC;
			} else if (nodeTypeStr.toLowerCase().equals("source")) {
				return NodeType.SOURCE;
			} else {
				return NodeType.UNDEF;
			}
		}
		
	}
}
