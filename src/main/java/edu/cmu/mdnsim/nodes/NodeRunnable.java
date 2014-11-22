package edu.cmu.mdnsim.nodes;

import java.util.concurrent.atomic.AtomicInteger;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;

public abstract class NodeRunnable implements Runnable {

	private Flow flow;
	private AtomicInteger totalBytesTransfered = new AtomicInteger(0);
	private AtomicInteger lostPacketNum = new AtomicInteger(0);
	private MessageBusClient msgBusClient;
	private String nodeId;
	private boolean integratedTest;
	
	/**
	 * Used to indicate NodeRunnable Thread to stop processing. Will be set to
	 * true when Master sends Terminate message for the flow attached to this
	 * NodeRunnable
	 */
	private boolean killed = false;
	/**
	 * Used to release resources like socket after the flow is terminated.
	 */
	private boolean stopped = false;

	private volatile boolean upStreamDone = false;

	public NodeRunnable(Flow flow, MessageBusClient msgBusClient, String nodeId) {
		this.flow = flow;
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
		return "/" + getNodeId() + "/" + this.getFlowId();
	}

	public void upStreamDoneSending(Flow flow) {
		System.err.println(getNodeId() + " - Upstream Done");
		this.msgBusClient.removeResource(getResourceName());
		this.upStreamDone = true;
	}

	public abstract void run();

	public String getFlowId() {
		return flow.getFlowId();
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

	public synchronized void stop() {
		stopped = true;
	}

	public synchronized boolean isStopped() {
		return stopped;
	}

	public Flow getFlow() {
		return flow;
	}

	public void setFlow(Flow flow) {
		this.flow = flow;
	}

	protected void sendEndMessageToDownstream() {
		try {
			System.err.println("Sending end message to "
					+ this.getFlow().findNodeMap(getNodeId())
							.get(Flow.DOWNSTREAM_URI) + "/" + this.getFlowId());
			msgBusClient.send(getFromPath(), this.getFlow()
					.findNodeMap(getNodeId()).get(Flow.DOWNSTREAM_URI)
					+ "/" + this.getFlowId(), "DELETE", this.getFlow());
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	private String getFromPath() {
		return "/" + getNodeId() + "/" + getFlowId();

	}

	public boolean isUpStreamDone() {
		return upStreamDone;
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

			System.out.println("I m interrupted :(");
			calculateAndReport();
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
				if (!integratedTest) {
					reportTransportationRate(transportationAverageRate,
							transportationInstantRate);
				}
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
				msg.setFlowId(NodeRunnable.this.getFlowId());
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
				msg.setFlowId(NodeRunnable.this.getFlowId());
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

		private String getUpStreamId() {
			String nodeIdStr = getNodeId();
			String[] nodeIds = NodeRunnable.this.getFlowId().split("-");
			for (int i = 1; i < nodeIds.length; i++) {
				if (nodeIdStr.equals(nodeIds[i])) {
					if (i < nodeIds.length - 1) {
						return nodeIds[i + 1];
					}
				}
			}
			throw new RuntimeException("Cannot find the upstream ID");
		}
	}
}