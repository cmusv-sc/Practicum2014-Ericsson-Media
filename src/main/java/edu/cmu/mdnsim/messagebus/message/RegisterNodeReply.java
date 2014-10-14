package edu.cmu.mdnsim.messagebus.message;

public class RegisterNodeReply extends MbMessage {

	private String nodeName;
	
	public RegisterNodeReply() {
		this("unknown");
	}
	
	public RegisterNodeReply(String nodeName) {
		this.nodeName = nodeName;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	
}
