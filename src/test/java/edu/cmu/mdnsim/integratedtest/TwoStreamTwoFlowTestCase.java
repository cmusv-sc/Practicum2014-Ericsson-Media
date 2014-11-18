package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

public class TwoStreamTwoFlowTestCase implements MessageBusTestCase {

	private MessageBusClient msgBusClient;
	private String streamID1;
	private String streamID2;
	
	public TwoStreamTwoFlowTestCase(MessageBusClient client, String simuID1, String simuID2) {
		msgBusClient = client;
		this.streamID1 = simuID1;
		this.streamID2 = simuID2;
	}
	
	@Override
	public void execute() throws MessageBusException {
		
		WorkConfig wc = WorkConfigFactory.getWorkConfig(WorkConfigFactory.Scenario.TWO_STREMS_TWO_FLOWS, streamID1, streamID2);
		
		try {
			msgBusClient.sendToMaster("/", "/work_config", "POST", wc);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
		
	}
	
	

}
