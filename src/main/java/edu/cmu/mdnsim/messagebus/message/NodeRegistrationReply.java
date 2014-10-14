package edu.cmu.mdnsim.messagebus.message;

public class NodeRegistrationReply extends MbMessage {
	
	private String _managerWarpURI;
	
	public NodeRegistrationReply(){
		super();
	}
	
	public NodeRegistrationReply(String managerWarpURI) {
		_managerWarpURI = managerWarpURI;
	}
	
	public String getManagerWarpURI() {
		return _managerWarpURI;
	}
	
	public void setManagerWarpURI(String managerWarpURI) {
		_managerWarpURI = managerWarpURI;
	}
	
}
