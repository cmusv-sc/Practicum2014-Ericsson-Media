package edu.cmu.mdnsim.messagebus.test;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.nodes.AbstractNode;
import edu.cmu.mdnsim.nodes.NodeType;



/**
 * Stimulus class simulates on behalf of web client. It directly talks to master
 * node and simulates different kinds of simulation request. It is regarded as a
 * proxy of web client.
 * 
 * @author JeremyFu
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
		stimulus.config(msgBusClient, NodeType.UNDEF, "STIMULUS");
		stimulus.register();
		Thread.sleep(1000 * 5);
		String simuID = "test-1";
		MessageBusTestCase testCase = new WorkConfigTestCase(stimulus.msgBusClient, simuID);
		stimulus.addTestCase(testCase);
		testCase = new StartSimulationTestCase(stimulus.msgBusClient);
		stimulus.addTestCase(testCase);
		testCase = new StopSimulationTestCase(stimulus.msgBusClient, simuID);
		
		
		
		stimulus.runTestCase(0, "Finish validate the WorkConfig");
		Thread.sleep(1000 * 5);
		stimulus.runTestCase(1, "Start the simulation");
		Thread.sleep(1000 * 3);
		
		
		
		
	}


	@Override
	public void executeTask(StreamSpec s) {
		
		/* 
		 * This method is unnecessary for Stimulus as itself doesn't execute any
		 * task 
		 *
		 */

		
	}

	
}

