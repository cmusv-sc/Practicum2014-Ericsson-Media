package edu.cmu.mdnsim.messagebus.message;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class StopSimulationRequest extends MbMessage {
	
	private String streamID;
	
	public StopSimulationRequest() {
		streamID = "default";
	}
	
	public StopSimulationRequest(String streamID) {
		this.streamID = streamID;
	}
	
	public void setStreamID(String streamID) {
		this.streamID = streamID;
	}
	
	public String getStreamID() {
		return streamID;
	}
	
}
