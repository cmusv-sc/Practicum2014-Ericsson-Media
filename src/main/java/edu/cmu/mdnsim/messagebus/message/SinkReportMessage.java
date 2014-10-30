package edu.cmu.mdnsim.messagebus.message;

public class SinkReportMessage extends MbMessage {
	
	String streamId;
	int totalBytes;
	String endTime;

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
	
	public String getEndTime() {
		return endTime;
	}
	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}
	
}
