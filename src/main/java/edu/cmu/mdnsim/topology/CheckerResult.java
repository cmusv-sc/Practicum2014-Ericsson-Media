package edu.cmu.mdnsim.topology;

public class CheckerResult {
	
	String nodeID;
	boolean hasError;
	String msg;
	
	CheckerResult() {
		hasError = false;
	}
	
	CheckerResult(String nodeID, String msg) {
		this.nodeID = nodeID;
		this.msg		= msg;
		this.hasError = false;
	}
	
	
}
