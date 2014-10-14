package edu.cmu.mdnsim.messagebus.message;

public class RegisterNodeContainerRequest extends MbMessage{
	
	private String label;
	private String nodeName;
	private String ncURI;
	
	public RegisterNodeContainerRequest() {
		
	}
	
	public RegisterNodeContainerRequest(String label, String nodeName, String ncURI) {
		this.label = label;
		this.nodeName = nodeName;
		this.ncURI = ncURI;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public String getNcURI() {
		return ncURI;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}
	
	public void setNcURI(String uri) {
		this.ncURI = uri;
	}
	
	
}
