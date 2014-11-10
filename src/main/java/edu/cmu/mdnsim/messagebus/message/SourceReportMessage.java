package edu.cmu.mdnsim.messagebus.message;

public class SourceReportMessage extends MbMessage {

	String flowId;
	int totalBytesTransferred;
	/**
	 * Indicates time when the event occurred.
	 * If event is start sending then it indicates start time.
	 * If event is done sending then it indicates end time. 
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

	
	public SourceReportMessage() {
		super();
		flowId = "";
		totalBytesTransferred = 0;
	}
	
	public String getFlowId() {
		return flowId;
	}
	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}
	
	public int getTotalBytesTransferred() {
		return totalBytesTransferred;
	}
	public void setTotalBytesTransferred(int totalBytesTransferred) {
		this.totalBytesTransferred = totalBytesTransferred;
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
}
