package edu.cmu.messagebus.message;

import edu.cmu.messagebus.NodeType;


public class NodeRegistrationMessage {
	
	private String _warpURI;
	private String _nodeIP;
	private int _port;
	private NodeType _type;
	
	public NodeRegistrationMessage() {
		_warpURI = null;
		_nodeIP = "";
		_port = 1099;
	}
	
	public NodeRegistrationMessage(String uri, String ip, int port, NodeType type) {
		this._warpURI = uri;
		this._nodeIP = ip;
		this._port = port;
		this._type = type;
	}
	
	public String getWarpURI() {
		return _warpURI;
	}
	
	public String getNodeIP() {
		return _nodeIP;
	}
	
	public int getPort() {
		return _port;
	}
	
	public NodeType getType() {
		return _type;
	}
	
	public void setWarpURI(String uri) {
		_warpURI = uri;
	}
	
	public void setNodeIP(String ip) {
		_nodeIP = ip;
	}
	
	public void setPort(int port) {
		_port = port;
	}
	
	public void setType(NodeType type) {
		_type = type;
	}
}
