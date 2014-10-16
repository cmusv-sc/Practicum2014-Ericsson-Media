package edu.cmu.mdnsim.messagebus.test;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

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
	
	
	public void config() throws MessageBusException {
		msgBusClient.config();
	}
	
	public void addTestCase(MessageBusTestCase testCase) {
		testCaseList.add(testCase);
	}
	
	public void runTestCase(int index) throws MessageBusException {
		
		testCaseList.get(index).execute();
		
	}
	
	
	public static void main(String[] args) throws MessageBusException, InterruptedException, UnknownHostException {
		
		Stimulus stimulus = new Stimulus();
		stimulus.config();
		stimulus.connect();
		Thread.sleep(1000 * 5);
		MessageBusTestCase testCase = new CreateNodeTest(stimulus.msgBusClient);
		stimulus.addTestCase(testCase);
		stimulus.runTestCase(0);
	}


	@Override
	public void executeTask(WorkSpecification ws) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void config(String nodeName) throws MessageBusException {
		// TODO Auto-generated method stub
		
	}
	
	
}

