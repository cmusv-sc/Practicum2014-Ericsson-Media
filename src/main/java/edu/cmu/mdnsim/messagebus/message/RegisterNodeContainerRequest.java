package edu.cmu.mdnsim.messagebus.message;

public class RegisterNodeContainerRequest extends MbMessage{
	
	private String label;
	private String ncURI;
	
	public RegisterNodeContainerRequest() {
		
	}
	
	public RegisterNodeContainerRequest(String label, String ncURI) {
		this.label = label;
		this.ncURI = ncURI;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getNcURI() {
		return ncURI;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public void setNcURI(String uri) {
		this.ncURI = uri;
	}
	
	
}
