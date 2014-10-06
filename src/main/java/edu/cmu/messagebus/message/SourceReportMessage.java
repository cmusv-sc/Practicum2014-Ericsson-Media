package edu.cmu.messagebus.message;

public class SourceReportMessage {

	String _streamId;
	int _totalBytes_transferred;
	String _startTime;

	public SourceReportMessage() {
		super();
		_streamId = "defaultStreamId";
		_totalBytes_transferred = 0;
	}
	
	public String getStreamId() {
		return _streamId;
	}
	public void setStreamId(String _streamId) {
		this._streamId = _streamId;
	}
	
	public int getTotalBytes_transferred() {
		return _totalBytes_transferred;
	}
	public void setTotalBytes_transferred(int _totalBytes_transferred) {
		this._totalBytes_transferred = _totalBytes_transferred;
	}
	
	public String getStartTime() {
		return _startTime;
	}
	public void setStartTime(String _startTime) {
		this._startTime = _startTime;
	}
}
