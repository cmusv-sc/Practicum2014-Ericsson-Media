package edu.cmu.mdnsim.messagebus.message;


public class StopSimulationRequest extends MbMessage {
	
	private String simuID;
	
	public StopSimulationRequest(String simuID) {
		this.simuID = simuID;
	}
	
	public void setSimulationID(String simuID) {
		this.simuID = simuID;
	}
	
	public String getSimuID() {
		return simuID;
	}
	
}
