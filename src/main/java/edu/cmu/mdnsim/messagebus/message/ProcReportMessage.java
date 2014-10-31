package edu.cmu.mdnsim.messagebus.message;

public class ProcReportMessage extends MbMessage {

	private String streamId;
	private int totalBytes;
	private String endTime;
	private String startTime;
	
	public ProcReportMessage() {
		super();
	}
	
	/* Getters */
	public String getStreamId() {
		return streamId;
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
	public void setStreamId(String streamId) {
		this.streamId = streamId;
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
	
}
