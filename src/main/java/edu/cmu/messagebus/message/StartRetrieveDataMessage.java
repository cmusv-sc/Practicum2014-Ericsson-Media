package edu.cmu.messagebus.message;

public class StartRetrieveDataMessage {
	
	private String _sourceIP;
	private int _sourcePort;
	
	public StartRetrieveDataMessage() {
		_sourceIP = "localhost";
		_sourcePort = 12300;
	}
	
	public StartRetrieveDataMessage(String sourceIP, int sourcePort) {
		_sourceIP = sourceIP;
		_sourcePort = sourcePort;
	}
	
	public String getSourceIP() {
		return _sourceIP;
	}
	
	public int getSourcePort() {
		return _sourcePort;
	}
	
	public void setSourceIP(String sourceIP) {
		_sourceIP = sourceIP;
	}
	
	public void setSourcePort(int sourcePort) {
		_sourcePort = sourcePort;
	}
	
	
}
