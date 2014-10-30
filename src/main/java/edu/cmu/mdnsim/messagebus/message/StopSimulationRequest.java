package edu.cmu.mdnsim.messagebus.message;


public class StopSimulationRequest extends MbMessage {
	
	private String simuID;
	
	public StopSimulationRequest() {
		simuID = "default";
	}
	
	public StopSimulationRequest(String simuID) {
		this.simuID = simuID;
	}
	
	public void setSimuID(String simuID) {
		this.simuID = simuID;
	}
	
	public String getSimuID() {
		return simuID;
	}
	
}
