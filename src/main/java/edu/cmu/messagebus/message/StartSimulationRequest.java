package edu.cmu.messagebus.message;

public class StartSimulationRequest {
	
	private String _nodeName;
	
	public StartSimulationRequest() {
		_nodeName = "";
	}
	
	public StartSimulationRequest (String name) {
		_nodeName = name;
	}
	
	public String getNode() {
		return _nodeName;
	}
	
	public void setNode(String node) {
		_nodeName = node;
	}
	
}
