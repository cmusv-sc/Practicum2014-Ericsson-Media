package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.StopSimulationRequest;

public class StopTwoStreamTwoFlowTestCase implements MessageBusTestCase {

	private MessageBusClient msgBusClient;
	private String streamID;
	
	public StopTwoStreamTwoFlowTestCase(MessageBusClient msgBusClient, String streamID) {
		this.msgBusClient = msgBusClient;
		this.streamID = streamID;
	}
	
	@Override
	public void execute() throws MessageBusException {
		
		WorkConfig wc = WorkConfigFactory.getWorkConfig("simu-test", WorkConfigFactory.Scenario.SINGLE_STREAM_SINGLE_FLOW, streamID);
		
		msgBusClient.sendToMaster("/", "/simulations", "POST", wc);
		
	}

}
