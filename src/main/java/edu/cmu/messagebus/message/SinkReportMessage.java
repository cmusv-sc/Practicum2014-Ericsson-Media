package edu.cmu.messagebus.message;

public class SinkReportMessage {
	
	String _streamId;
	int _totalBytes;
	String _endTime;

	public SinkReportMessage() {
		_streamId = "defaultStreamID";
		_totalBytes = 0;
	}
	
	public String getStreamId() {
		return _streamId;
	}
	public void setStreamId(String _streamId) {
		this._streamId = _streamId;
	}
	
	public int getTotalBytes() {
		return _totalBytes;
	}
	public void setTotalBytes(int _totalBytes) {
		this._totalBytes = _totalBytes;
	}
	
	public String getEndTime() {
		return _endTime;
	}
	public void setEndTime(String _endTime) {
		this._endTime = _endTime;
	}
	
}
