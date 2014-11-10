package edu.cmu.mdnsim.messagebus.message;

public class ProcReportMessage extends MbMessage {

	private String flowId;
	private int totalBytes;
	private String endTime;
	private String startTime;
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
	
	
	public ProcReportMessage() {
		super();
	}
	
	/* Getters */
	public String getFlowId() {
		return flowId;
	}
	
	public int getTotalBytes() {
		return totalBytes;
	}
	
	public String getEndTime() {
		return endTime;
	}
	
	public String getStartTime() {
		return startTime;
	}
	
	/* Setters */
	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}
	
	public void setTotalBytes(int totalBytes) {
		this.totalBytes = totalBytes;
	}
	
	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}
	
	public void setStartTime(String startTime) {
		this.startTime = startTime;
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
