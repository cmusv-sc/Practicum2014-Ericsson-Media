package edu.cmu.mdnsim.messagebus.message;

public class RegisterNodeContainerRequest extends MbMessage{
	
	private String label;
	private String containerLabel;
	private String ncURI;
	
	public RegisterNodeContainerRequest() {
		
	}
	
	public RegisterNodeContainerRequest(String label, String containerLabel, String ncURI) {
		this.label = label;
		this.containerLabel = containerLabel;
		this.ncURI = ncURI;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getContainerLabel() {
		return containerLabel;
	}
	
	public String getNcURI() {
		return ncURI;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public void setContainerLabel(String containerName) {
		this.containerLabel = containerName;
	}
	
	public void setNcURI(String uri) {
		this.ncURI = uri;
	}
	
	
}
