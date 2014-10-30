package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.StopSimulationRequest;

public class StopSimulationTestCase implements MessageBusTestCase {

	private MessageBusClient msgBusClient;
	private String simuID;
	
	public StopSimulationTestCase(MessageBusClient msgBusClient, String simuID) {
		this.msgBusClient = msgBusClient;
		this.simuID = simuID;
	}
	
	@Override
	public void execute() throws MessageBusException {
		
		StopSimulationRequest req = new StopSimulationRequest(simuID);
		msgBusClient.sendToMaster("/", "/simulations", "POST", req);
		
	}

}
