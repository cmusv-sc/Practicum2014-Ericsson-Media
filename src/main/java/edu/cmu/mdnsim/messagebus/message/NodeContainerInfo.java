package edu.cmu.mdnsim.messagebus.message;

public class NodeContainerInfo extends MbMessage {
	
	private String ncName;
	private String ncURL;
	
	public NodeContainerInfo() {
		super();
	}
	
	public NodeContainerInfo(String name, String url) {
		ncName = name;
		ncURL  = url;
	}
	
	public String getNcName() {
		return ncName;
	}
	
	public String getNcURL() {
		return ncURL;
	}
	
	public void setNcName(String name) {
		ncName = name;
	}
	
	public void setNcURL(String url) {
		ncURL = url;
	}

}
