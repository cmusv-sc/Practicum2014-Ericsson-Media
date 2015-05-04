package edu.cmu.mdnsim.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;

/**
 * A runnable that represents an individual Stream.
 * <p>It will run in a separate thread and will have dedicated reporting thread attached to it.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public abstract class NodeRunnable implements Runnable {
	
	
	/**
	 * If a packet is not received within {@link TIMEOUT_FOR_PACKET_LOSS} seconds, the packet is regarded lost.
	 */
	public static final int TIMEOUT_FOR_PACKET_LOSS = 1;
	
	public static final int INTERVAL_IN_MILLISECOND = 1000;
	
	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.node-runnable");
	private Stream stream;
	private AtomicLong totalBytesTransfered = new AtomicLong(0);
	private AtomicLong lostPacketNum = new AtomicLong(0);
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

	/**
	 * Construct a NodeRunnable.
	 * @param stream the stream this NodeRunnble is associated with
	 * @param msgBusClient the MessageBusClient that the NodeRunnable can use to report to the management layer
	 * @param nodeId the node id that the NodeRunnable is associated with
	 * @param cleaner the object that this NodeRunnble can use for cleaning up
	 */
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

	public String getNodeId() {
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

	public long getTotalBytesTranfered() {
		return totalBytesTransfered.get();
	}

	public void setTotalBytesTranfered(long totalBytesTranfered) {
		this.totalBytesTransfered.set(totalBytesTranfered);
	}

	public synchronized long getLostPacketNum() {
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
	 * Resets the NodeRunnable. The NodeRunnable should be interrupted (set killed),
	 * and set reset flag as actions for clean up is different from being killed.
	 */
	public synchronized void reset() {
		this.killed = true;
		this.reset = true;
		
		logger.debug("reset(): Reset the " + this.nodeId);
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
	public String getUpStreamId() {
		Stream stream = NodeRunnable.this.getStream();
		for(Flow flow : stream.getFlowList()){
			Map<String, String> nodeMap = flow.findNodeMap(nodeId);
			if(nodeMap != null){
				return nodeMap.get(Flow.UPSTREAM_ID);
			}
		}
		return null;	
	}
	
	/**
	 * Gets the set of ids of the down stream.
	 * @return a set of down stream ids
	 */
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
	
	/**
	 * Gets the set of URI of the down stream.
	 * @return a set of down stream URIs
	 */
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
	
	/**
	 * Sets report to the 
	 * 
	 * 
	 * management layer.
	 * @param streamReportMessage
	 */
	public void sendStreamReport(StreamReportMessage streamReportMessage) {
		String fromPath = "/" + this.getNodeId() + "/" + this.getStreamId();
		streamReportMessage.from(this.getNodeId());
		
		try {
			msgBusClient.sendToMaster(fromPath, "/stream_report", "POST", streamReportMessage);
		} catch (MessageBusException e) {
			e.printStackTrace();
			logger.error(e.toString());
		};
	}
	
	abstract void clean();

}
