package edu.cmu.mdnsim.messagebus.message;

public class SndDataMessage {

	private String _sinkIP;
	private int _sinkPort;
	private int _dataSize;
	private int _dataRate;
	private String _streamID;
	
	public SndDataMessage() {
		super();
	}
	
	public SndDataMessage(String sinkIP, int sinkPort) {
		_sinkIP = sinkIP;
		_sinkPort = sinkPort;
		_dataSize = 1024;
		_dataRate = 1;
	}
	
	public String getSinkIP() {
		return _sinkIP;
	}
	
	public int getSinkPort() {
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
	
	public void setSinkIP(String sinkIP) {
		_sinkIP = sinkIP;
	}
	
	public void setSinkPort(int sinkPort) {
		_sinkPort = sinkPort;
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
