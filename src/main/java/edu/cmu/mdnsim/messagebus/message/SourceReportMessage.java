package edu.cmu.mdnsim.messagebus.message;

public class SourceReportMessage extends MbMessage {

	String streamId;
	int totalBytesTransferred;
	String startTime;

	public SourceReportMessage() {
		super();
		streamId = "";
		totalBytesTransferred = 0;
	}
	
	public String getStreamId() {
		return streamId;
	}
	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}
	
	public int getTotalBytesTransferred() {
		return totalBytesTransferred;
	}
	public void setTotalBytesTransferred(int totalBytesTransferred) {
		this.totalBytesTransferred = totalBytesTransferred;
	}
	
	public String getStartTime() {
		return startTime;
	}
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
}
