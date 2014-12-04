package edu.cmu.mdnsim.integratedtest;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * This interface defines the interface for all test cases. All test cases should
 * have the execute() method to be called by Stimulus.java
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public interface MessageBusTestCase {

	public void execute() throws MessageBusException;
	
	
}
