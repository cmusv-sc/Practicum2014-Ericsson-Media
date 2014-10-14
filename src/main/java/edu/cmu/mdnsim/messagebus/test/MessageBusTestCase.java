package edu.cmu.mdnsim.messagebus.test;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

public interface MessageBusTestCase {

	public void execute() throws MessageBusException;
	
	
}
