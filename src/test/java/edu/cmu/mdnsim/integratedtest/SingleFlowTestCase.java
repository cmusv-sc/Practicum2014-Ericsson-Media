package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.integratedtest.WorkConfigFactory.Scenario;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
 * This test case is to test the WorkConfig input. A fake WorkConfig is 
 * generated and sent to the master. The master should generate three nodes, 
 * one sink in NodeContainer with label tomato, one processing node in 
 * NodeContainer with label apple, the other(source) in orange. This WorkConfig 
 * is with streamID test-1. This test case simulates the simple one flow 
 * topology (source ->...-> sink) in the simulator.
 * 
 * @author Jeremy Fu
 * @author Vinay Kumar Vavili
 * @author Jigar Patel
 * @author Hao Wang
 *
 */
public class SingleFlowTestCase implements MessageBusTestCase {
	
	private MessageBusClient msgBusClient;
	private String simuID;
	
	public SingleFlowTestCase(MessageBusClient client, String simuID) {
		msgBusClient = client;
		this.simuID = simuID;
	}
	
	@Override
	public void execute() {
		
		WorkConfig wc = WorkConfigFactory.getWorkConfig("simu-test", Scenario.SINGLE_STREAM_SINGLE_FLOW, "stream1");
		
		try {
			msgBusClient.sendToMaster("/", "/work_config", "POST", wc);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	
}

	
