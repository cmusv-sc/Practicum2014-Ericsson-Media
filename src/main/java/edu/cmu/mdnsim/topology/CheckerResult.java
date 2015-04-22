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
		this.hasError = true;
	}
	
	public boolean hasError() {
		return this.hasError;
	}
	
	public String errorMessage() {
		if (!hasError) {
			throw new RuntimeException("CheckerResult doesn't have errorMessgae.");
		}
		return msg;
	}
	
}
