package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class ResetTestCase implements MessageBusTestCase {
	
	private MessageBusClient msgBusClient;


	
	public ResetTestCase (MessageBusClient client) {
		msgBusClient = client;
	}
	@Override
	public void execute() throws MessageBusException {
		
		System.out.println("send nodes");
		msgBusClient.sendToMaster("/", "/nodes", "DELETE", null);
		
	}

}
