package edu.cmu.mdnsim.messagebus.message;

import edu.cmu.mdnsim.nodes.NodeType;

public class CreateNodeRequest extends MbMessage {
	
	private NodeType nodeType;
	private String nodeClass;
	private String ncLabel; //Node Container label
	
	public CreateNodeRequest() {
		this(NodeType.UNDEF, "default", "");
	}
	
	public CreateNodeRequest(NodeType type) {
		this(type, "default", "");
		nodeType = type;
	}
	
	public CreateNodeRequest(NodeType type, String label, String nClass) {
		nodeType = type;
		ncLabel = label;
		nodeClass = nClass;
	}
	
	public NodeType getNodeType() {
		return nodeType;
	}
	
	public String getNcLabel() {
		return ncLabel;
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
	
}
