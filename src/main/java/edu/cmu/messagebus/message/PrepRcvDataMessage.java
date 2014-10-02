package edu.cmu.messagebus.message;


public class PrepRcvDataMessage {
	
	private String _sourceWarpURI;
	
	private int _dataSize;
	private int _streamRate;
	private String _streamID;
	
	public PrepRcvDataMessage () {
		super();
	}
	
	public PrepRcvDataMessage(String sourceWarpURI) {
		_sourceWarpURI = sourceWarpURI;
		_dataSize = 1024;
		_streamRate = 1;
		_streamID = "";
	}
	
	public String getSourceWarpURI() {
		return _sourceWarpURI;
	}
	
	public int getDataSize() {
		return _dataSize;
	}
	
	public int getstreamRate() {
		return _streamRate;
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
	
	public void setStreamRate(int streamRate) {
		_streamRate = streamRate;
	}
	
	public void setStreamID(String streamID) {
		_streamID = streamID;
	}

	
}
