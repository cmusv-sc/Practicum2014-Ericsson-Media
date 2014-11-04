package edu.cmu.mdnsim.messagebus.message;

public class SinkReportMessage extends MbMessage {
	
	String streamId;
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

	public SinkReportMessage() {
		streamId = "";
		totalBytes = 0;
	}
	
	public String getStreamId() {
		return streamId;
	}
	public void setStreamId(String streamId) {
		this.streamId = streamId;
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
	
}
