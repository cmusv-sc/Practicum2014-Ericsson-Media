package edu.cmu.mdnsim.messagebus;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

/**
 * 
 * This interface defines the way that how {@link edu.cmu.mdnsim.server.Master} 
 * can communicate with other components in the system.
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public interface MessageBusServer {

	public static final String SERVER_ID = "mdnsim";
	
	/**
	 * 
	 * Configures the MessageBusServer. The MessageBusServer starts the message
	 * broker, connects server to broker and gets ready for components. The 
	 * config() is blocked until it completes setting.
	 * 
	 * @throws MessageBusException
	 */
	public void config() throws MessageBusException;
	
	/**
	 * 
	 * Sends a message to a client.
	 * 
	 * @param fromPath The source of resource to send message
	 * @param dstURI The destination of resource to receive the message
	 * @param method The method of the message
	 * @param msg The message to receive
	 * @throws MessageBusException
	 */
	public void send(String fromPath, String dstURI, String method, MbMessage msg) throws MessageBusException;
	
	public void addMethodListener(String path, String method, Object object, String objectMethod) throws MessageBusException;
	
	public String getURL();
	
}
