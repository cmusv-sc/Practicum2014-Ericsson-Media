package edu.cmu.mdnsim.messagebus;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.Message;

public interface MessageBusServer {

	public void config() throws MessageBusException;
	
	public void register() throws MessageBusException;
	
	public void send(String fromPath, String dstURI, String method, Message msg) throws MessageBusException;
	
	public void addMethodListener(String path, String method, Object object, String objectMethod) throws MessageBusException;
	
	public Object getDomain();
	
}
