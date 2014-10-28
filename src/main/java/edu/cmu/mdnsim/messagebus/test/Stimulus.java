package edu.cmu.mdnsim.messagebus.test;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.nodes.AbstractNode;
import edu.cmu.mdnsim.nodes.NodeType;




public class Stimulus extends AbstractNode {
	
	List<MessageBusTestCase> testCaseList = new LinkedList<MessageBusTestCase>();
	
	public Stimulus() throws UnknownHostException, MessageBusException {
		super();
	}
	
	
//	public void config() throws MessageBusException {
//		msgBusClient.config();
//	}
	
	public void addTestCase(MessageBusTestCase testCase) {
		testCaseList.add(testCase);
	}
	
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
		MessageBusTestCase testCase = new WorkConfigTestCase(stimulus.msgBusClient);
		stimulus.addTestCase(testCase);
		testCase = new StartSimulationTestCase(stimulus.msgBusClient);
		stimulus.addTestCase(testCase);
		stimulus.runTestCase(0, "Finish validate the WorkConfig");
		Thread.sleep(1000 * 5);
		stimulus.runTestCase(1, "Start the simulation");
		
		
		
	}


	@Override
	public void executeTask(WorkConfig ws) {
		// TODO Auto-generated method stub
		
	}
	
}

