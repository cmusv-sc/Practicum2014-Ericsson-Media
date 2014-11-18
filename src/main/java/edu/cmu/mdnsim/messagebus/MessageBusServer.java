package edu.cmu.mdnsim.messagebus;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public interface MessageBusServer {

	public void config() throws MessageBusException;
	
	public void send(String fromPath, String dstURI, String method, MbMessage msg) throws MessageBusException;
	
	public void addMethodListener(String path, String method, Object object, String objectMethod) throws MessageBusException;
	
}
