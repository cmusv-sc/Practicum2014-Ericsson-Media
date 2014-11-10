package edu.cmu.mdnsim.messagebus.message;

import edu.cmu.mdnsim.nodes.NodeType;

public class CreateNodeRequest extends MbMessage {
	
	private String nodeType;
	private String nodeClass;
	private String nodeId; // node ID: tomato:sink1
	private String ncLabel; // node container label: tomato
	
	public CreateNodeRequest() {
		this("undefined", "default", "");
	}
	
	public CreateNodeRequest(String type) {
		this(type, "default", "");
		nodeType = type;
	}
	
	/**
	 * Constructor for CreateNodeRequest.
	 * @param type
	 * @param label The label of node container
	 * @param nClass the node class name
	 */
	public CreateNodeRequest(String type, String nodeId, String nClass) {
		nodeType = type;
		this.nodeId = nodeId;
		ncLabel = nodeId.split(":")[0];
		nodeClass = nClass;
	}
	
	public String getNodeType() {
		return nodeType;
	}
	
	public String getNcLabel() {
		return ncLabel;
	}
	
	public String getNodeId() {
		return nodeId;
	}
	
	public String getNodeClass() {
		return nodeClass;
	}
	
	public void setNodeType(String type) {
		nodeType = type;
	}
	
	public void setNcLabel(String label) {
		ncLabel = label;
	}
	
	public void setNodeClass(String className) {
		nodeClass = className;
	}
	
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}
	
}
