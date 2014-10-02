package edu.cmu.messagebus.message;

public class SinkReportMessage {
	
	String _streamId = "defaultStreamId";
	long _totalTime = 0;
	int _totalBytes = 0;
	
	public String getStreamId() {
		return _streamId;
	}
	public void setStreamId(String _streamId) {
		this._streamId = _streamId;
	}
	
	public long getTotalTime() {
		return _totalTime;
	}
	public void setTotalTime(long _totalTime) {
		this._totalTime = _totalTime;
	}
	
	public int getTotalBytes() {
		return _totalBytes;
	}
	public void setTotalBytes(int _totalBytes) {
		this._totalBytes = _totalBytes;
	}
	
}
