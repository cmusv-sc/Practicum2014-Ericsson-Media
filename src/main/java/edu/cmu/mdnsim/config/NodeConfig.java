package edu.cmu.mdnsim.config;

public class NodeConfig {

	private String id;
	private String nodeType; 
	private String label; 
	private int bitrate;
	private String URI;
	 
	private String upStreamNodeId;
	private String desIP;
	private int desPort;
	 	 
	public static class Builder{
		
		private final String nodeType; 
		private String id = "";
		private String label = ""; 
		private int bitrate = 500;
		private String URI = "";
		private String upStreamNodeId = "";
		private String desIP = "";
		private int desPort = 0;
		
		public Builder(String nodeType){
			this.nodeType = nodeType;
		}
		
		public Builder id(String id){
			this.id = id;
			return this;
		}
		public Builder label(String label){
			this.label = label;
			return this;
		}
		public Builder bitrate(int bitrate){
			this.bitrate = bitrate;
			return this;
		}
		public Builder URI(String URI){
			this.URI = URI;
			return this;
		}
		public Builder desIP(String desIP){
			this.desIP = desIP;
			return this;
		}
		public Builder desPort(int desPort){
			this.desPort = desPort;
			return this;
		}
		public Builder upStreamNodeId(String upStreamNodeId){
			this.upStreamNodeId = upStreamNodeId;
			return this;
		}
		
		public NodeConfig build(){
			return new NodeConfig(this);
		}
	}
	
	public NodeConfig(){
	}
	
	public NodeConfig(Builder builder){
		this.nodeType = builder.nodeType;
		this.id = builder.id;
		this.label = builder.label;
		this.bitrate = builder.bitrate;
		this.URI = builder.URI;
		this.desIP = builder.desIP;
		this.desPort = builder.desPort;
		this.upStreamNodeId = builder.upStreamNodeId;
	}
	
	public String getNodeType() {
		return nodeType;
	}
	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public int getBitrate() {
		return bitrate;
	}
	public void setBitrate(int bitrate) {
		this.bitrate = bitrate;
	}
	public String getURI() {
		return URI;
	}
	public void setURI(String uRI) {
		URI = uRI;
	}
	public String getDesIP() {
		return desIP;
	}
	public void setDesIP(String desIP) {
		this.desIP = desIP;
	}
	public int getDesPort() {
		return desPort;
	}
	public void setDesPort(int desPort) {
		this.desPort = desPort;
	}
	 public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUpStreamNodeId() {
		return upStreamNodeId;
	}
	public void setUpStreamNodeId(String upStreamNodeId) {
		this.upStreamNodeId = upStreamNodeId;
	}
	
}
