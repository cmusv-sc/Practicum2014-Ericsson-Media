package edu.cmu.messagebus.message;

public class SndDataMessage {

	private String _sinkIP;
	private int _sinkPort;
	private int _dataSize;
	private int _dataRate;
	private String _streamID;
	
	
	public SndDataMessage(String sourceIP, int sourcePort) {
		_sinkIP = sourceIP;
		_sinkPort = sourcePort;
		_dataSize = 1024;
		_dataRate = 1;
	}
	
	public String getSourceIP() {
		return _sinkIP;
	}
	
	public int getSourcePort() {
		return _sinkPort;
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
	
	public void setSourceIP(String sourceIP) {
		_sinkIP = sourceIP;
	}
	
	public void setSourcePort(int sourcePort) {
		_sinkPort = sourcePort;
	}
	
	public void setDataSize(int dataSize) {
		_dataSize = dataSize;
	}
	
	public void setDataRate(int dataRate) {
		_dataRate = dataRate;
	}
	
	public void setStreamID(String streamID) {
		_streamID = streamID;
	}
}
