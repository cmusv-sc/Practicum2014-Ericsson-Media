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

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class TestRest extends AbstractNode {
	
	/**
	 * The list of test cases. Just add the test cases
	 */
	List<MessageBusTestCase> testCaseList = new LinkedList<MessageBusTestCase>();
	
	
	public TestRest() throws UnknownHostException, MessageBusException {
		super();
	}
	
	
	public static void main(String[] args) throws MessageBusException, InterruptedException, UnknownHostException {
		
		MessageBusClient msgBusClient = new MessageBusClientWarpImpl();
		msgBusClient.config();
		msgBusClient.connect();
		
		TestRest tester = new TestRest();
		tester.config(msgBusClient, "undefined", "STIMULUS");
		tester.register();
		Thread.sleep(1000 * 5);
		String simuID = "test-relay";
		
		/* Add WorkConfig test case */
		
		WorkConfig wc = WorkConfigFactory.getWorkConfig(simuID, Scenario.ONE_STREAM_TWO_FLOWS, "stream1");
		
		try {
			msgBusClient.sendToMaster("/", "/work_config", "POST", wc);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
		
		//TODO: add terminate stream
		Thread.sleep(1000 * 3);
		System.err.println("RESET!!!!");
		msgBusClient.sendToMaster("/", "/nodes", "DELETE", null);
		
		
		
		
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


