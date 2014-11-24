package edu.cmu.mdnsim.integratedtest;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.integratedtest.WorkConfigFactory.Scenario;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.nodes.AbstractNode;

public class TestStopFlow extends AbstractNode {
	
	
	
	public TestStopFlow() throws UnknownHostException, MessageBusException {
		super();
	}
	
	
	public static void main(String[] args) throws MessageBusException, InterruptedException, UnknownHostException {
		
		MessageBusClient msgBusClient = new MessageBusClientWarpImpl();
		msgBusClient.config();
		msgBusClient.connect();
		
		TestRest tester = new TestRest();
		tester.config(msgBusClient, "undefined", "Test-Stop-Flow");
		tester.register();
		Thread.sleep(1000 * 5);
		String simuID = "test-stop-flow";
		
		/* Add WorkConfig test case */
		
		WorkConfig wc = WorkConfigFactory.getWorkConfig(simuID, Scenario.ONE_STREAM_TWO_FLOWS, "stream1");

		msgBusClient.sendToMaster("/", "/work_config", "POST", wc);
		
		Thread.sleep(1000 * 3);
		
		Stream stream = wc.getStreamList().get(0);
		Flow removedFlow = stream.getFlowList().remove(0);
		
		msgBusClient.sendToMaster("/", "/simulations", "POST", wc);
		
		Thread.sleep(1000 * 3);
		
		stream.getFlowList().remove(0);
		stream.getFlowList().add(removedFlow);
		
		msgBusClient.sendToMaster("/", "/simulations", "POST", wc);
		
		
		
		
	}

	@Override
	public void terminateTask(Flow streamSpec) {

		/* 
		 * This method is unnecessary for Stimulus as itself doesn't execute any
		 * task 
		 *
		 */
		
	}

	@Override
	public void releaseResource(Flow streamSpec) {
		
		/* 
		 * This method is unnecessary for Stimulus as itself doesn't execute any
		 * task 
		 *
		 */
		
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void executeTask(Message request, Stream stream) {
		// TODO Auto-generated method stub
		
	}

	
}


