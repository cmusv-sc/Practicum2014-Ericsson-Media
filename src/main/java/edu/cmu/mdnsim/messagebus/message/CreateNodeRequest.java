package edu.cmu.mdnsim.messagebus.message;

import edu.cmu.mdnsim.nodes.NodeType;

public class CreateNodeRequest extends MbMessage {
	
	private NodeType nodeType;
	private String nodeClass;
	private String nodeId; // node ID: tomato:sink1
	private String ncLabel; // node container label: tomato
	
	public CreateNodeRequest() {
		this(NodeType.UNDEF, "default", "");
	}
	
	public CreateNodeRequest(NodeType type) {
		this(type, "default", "");
		nodeType = type;
	}
	
	/**
	 * Constructor for CreateNodeRequest.
	 * @param type
	 * @param label The label of node container
	 * @param nClass the node class name
	 */
	public CreateNodeRequest(NodeType type, String nodeId, String nClass) {
		nodeType = type;
		this.nodeId = nodeId;
		ncLabel = nodeId.split(":")[0];
		nodeClass = nClass;
	}
	
	public NodeType getNodeType() {
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
	
	public void setNodeType(NodeType type) {
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
