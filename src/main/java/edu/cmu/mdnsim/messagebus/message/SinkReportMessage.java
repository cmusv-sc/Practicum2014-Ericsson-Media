package edu.cmu.mdnsim.messagebus.message;

public class SinkReportMessage extends MbMessage {
	
	String flowId;
	int totalBytes;
	String endTime; // Indicates time (start or end) of any type of operation
	/**
	 * Indicates time when the event occurred.
	 * If event is start sending/receiving then it indicates start time.
	 * If event is done sending/receiving then it indicates end time. 
	 */
	String time; 
	/**
	 * Indicate what event occurred which triggered sending of report message
	 */
	private EventType eventType;
	/**
	 * It can be either node which is sending data to this node 
	 * or node which is receiving data from this node
	 */
	private String destinationNodeId;

	private String currentRate;
	private String averageRate;
	private String packetLoss;
	
	public SinkReportMessage() {
		flowId = "";
		totalBytes = 0;
	}
	
	public String getFlowId() {
		return flowId;
	}
	public void setFlowId(String streamId) {
		this.flowId = streamId;
	}
	
	public int getTotalBytes() {
		return totalBytes;
	}
	public void setTotalBytes(int totalBytes) {
		this.totalBytes = totalBytes;
	}
	
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}

	public EventType getEventType() {
		return eventType;
	}

	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}

	public String getDestinationNodeId() {
		return destinationNodeId;
	}

	public void setDestinationNodeId(String destinationNodeId) {
		this.destinationNodeId = destinationNodeId;
	}

	public String getCurrentRate() {
		return currentRate;
	}

	public void setCurrentRate(String currentRate) {
		this.currentRate = currentRate;
	}

	public String getAverageRate() {
		return averageRate;
	}

	public void setAverageRate(String averageRate) {
		this.averageRate = averageRate;
	}

	public String getPacketLoss() {
		return packetLoss;
	}

	public void setPacketLoss(String packetLoss) {
		this.packetLoss = packetLoss;
	}
	
}
