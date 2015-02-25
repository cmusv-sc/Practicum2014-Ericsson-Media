package edu.cmu.mdnsim.messagebus.message;

import edu.cmu.mdnsim.nodes.AbstractNode;
import edu.cmu.mdnsim.server.Master;
/**
 * 
 * This message is instantiated by instances of subclasses of {@link AbstractNode}
 * and sent to {@link Master} to register an instance of subclass of {@link AbstractNode}
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class RegisterNodeRequest extends MbMessage {
	
	private String _URI;
	private String _nodeIP;
	private String _nodeName;
	private int _port;
	
	public RegisterNodeRequest() {
		_URI = null;
		_nodeIP = "";
		_port = 1099;
	}
	
	public RegisterNodeRequest(String uri, String ip, int port) {
		this._URI = uri;
		this._nodeIP = ip;
		this._port = port;
	}
	
	public String getURI() {
		return _URI;
	}
	
	public String getNodeIP() {
		return _nodeIP;
	}
	
	public int getPort() {
		return _port;
	}
	
	
	public void setURI(String uri) {
		_URI = uri;
	}
	
	public void setNodeIP(String ip) {
		_nodeIP = ip;
	}
	
	public void setPort(int port) {
		_port = port;
	}
	
	
	public String getNodeName() {
		return _nodeName;
	}

	public void setNodeName(String _nodeName) {
		this._nodeName = _nodeName;
	}
}
