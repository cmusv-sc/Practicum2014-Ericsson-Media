package edu.cmu.messagebus.message;

public class NodeRegistrationReply {
	
	private String _managerWarpURI;
	
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
