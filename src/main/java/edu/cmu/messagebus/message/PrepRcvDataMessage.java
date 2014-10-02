package edu.cmu.messagebus.message;


public class PrepRcvDataMessage {
	
	private String _sourceWarpURI;
	
	private int _dataSize;
	private int _dataRate;
	private String _streamID;
	
	public PrepRcvDataMessage () {
		super();
	}
	
	public PrepRcvDataMessage(String sourceWarpURI) {
		_sourceWarpURI = sourceWarpURI;
		_dataSize = 1024;
		_dataRate = 1;
		_streamID = "";
	}
	
	public String getSourceWarpURI() {
		return _sourceWarpURI;
	}
	
	public int getDataSize() {
		return _dataSize;
	}
	
	public int getDataRate() {
		return _dataRate;
	}
	
	public String getStreamID() {
		return _streamID;
	}
	
	public void setSourceWarpURI(String sourceWarpURI) {
		_sourceWarpURI = sourceWarpURI;
	}
	
	public void setDataSize(int dataSize) {
		_dataSize = dataSize;
	}
	
	public void setDataRate(int streamRate) {
		_dataRate = streamRate;
	}
	
	public void setStreamID(String streamID) {
		_streamID = streamID;
	}

	
}
