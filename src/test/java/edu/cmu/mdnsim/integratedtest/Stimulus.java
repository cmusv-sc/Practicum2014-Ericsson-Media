package edu.cmu.mdnsim.integratedtest;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.nodes.AbstractNode;



/**
 * Stimulus class simulates on behalf of web client. It directly talks to master
 * node and simulates different kinds of simulation request. It is regarded as a
 * proxy of web client.
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class Stimulus extends AbstractNode {
	
	/**
	 * The list of test cases. Just add the test cases
	 */
	List<MessageBusTestCase> testCaseList = new LinkedList<MessageBusTestCase>();
	
	
	public Stimulus() throws UnknownHostException, MessageBusException {
		super();
	}
	
	/**
	 * Add new test case to the list
	 * 
	 * @param testCase
	 */
	public void addTestCase(MessageBusTestCase testCase) {
		testCaseList.add(testCase);
	}
	
	/**
	 * Run the test case on demand
	 * @param index
	 * @param message
	 * @throws MessageBusException
	 */
	public void runTestCase(int index, String message) throws MessageBusException {
		
		testCaseList.get(index).execute();
		System.out.println("[INFO]Stimulus.runTestCase(): " + message);
	}
	
	public static void main(String[] args) throws MessageBusException, InterruptedException, UnknownHostException {
		
		MessageBusClient msgBusClient = new MessageBusClientWarpImpl();
		msgBusClient.config();
		msgBusClient.connect();
		
		Stimulus stimulus = new Stimulus();
		stimulus.config(msgBusClient, "undefined", "STIMULUS");
		stimulus.register();
		Thread.sleep(1000 * 5);
		String simuID = "test-1";
		
		/* Add WorkConfig test case */
		MessageBusTestCase testCase = new SingleFlowTestCase(stimulus.msgBusClient, simuID);
		stimulus.addTestCase(testCase);
		
		/* Add RESET test case */
		testCase = new ResetTestCase(stimulus.msgBusClient);
		stimulus.addTestCase(testCase);
//		/* Add stop simulation test case */
//		testCase = new StopSimulationTestCase(stimulus.msgBusClient, simuID);
//		stimulus.addTestCase(testCase);
		
		/* Create topology specified by WorkConfig */
		stimulus.runTestCase(0, "Start to send the WorkConfig");
		Thread.sleep(1000 * 2);
		
//		stimulus.runTestCase(1, "Reset");
		
		
//		/* Stop the simulation */
//		stimulus.runTestCase(2, "Stop the simulation");
		
		
		
		
		
	}


	public void executeTask(Stream s) {
		
		/* 
		 * This method is unnecessary for Stimulus as itself doesn't execute any
		 * task 
		 *
		 */

		
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

