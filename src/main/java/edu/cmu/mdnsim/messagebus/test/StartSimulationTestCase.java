package edu.cmu.mdnsim.messagebus.test;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.StartSimulationRequest;

/**
 * 
 * This test case is to simulate a click action on "start simulation" on web 
 * client. This will trigger the master to start the stimulation specified by 
 * all WorkConfigs stored in master.
 * 
 * @author Jeremy Fu, Vinay Kumar Vavili, Jigar Patel, Hao Wang
 *
 */
public class StartSimulationTestCase implements MessageBusTestCase{

	private MessageBusClient msgBusClient;
	
	public StartSimulationTestCase(MessageBusClient msgBusClient) {
		this.msgBusClient = msgBusClient;
	}
	
	@Override
	public void execute() throws MessageBusException {
		
		try {
			msgBusClient.sendToMaster("/", "/start_simulation", "POST", new StartSimulationRequest());
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
		
	}

}
