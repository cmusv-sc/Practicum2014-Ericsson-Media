package edu.cmu.messagebus.message;

public class SourceReportMessage {

	String _streamId = "defaultStreamId";
	int _totalBytes_transferred = 0;
	
	public SourceReportMessage() {
		super();
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
	
}
