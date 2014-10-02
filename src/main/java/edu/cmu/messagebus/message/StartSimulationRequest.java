package edu.cmu.messagebus.message;

public class StartSimulationRequest {
	
	private String _sourceNodeName;
	private String _sinkNodeName;
	
	public StartSimulationRequest() {
		_sourceNodeName = "";
		_sinkNodeName = "";
	}
	
	public StartSimulationRequest (String sourceNodeName, String sinkNodeName) {
		_sourceNodeName = sourceNodeName;
		_sinkNodeName = sinkNodeName;
	}
	
	public String getSourceNodeName() {
		return _sourceNodeName;
	}
	
	public String getSinkNodeName() {
		return _sinkNodeName;
	}
	
	public void setSourceNodeName(String sourceNodeName) {
		_sourceNodeName = sourceNodeName;
	}
	
	public void setSinkNodeName(String sinkNodeName) {
		_sinkNodeName = sinkNodeName;
	}
	
}
