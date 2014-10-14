package edu.cmu.mdnsim.messagebus;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.Message;
import edu.cmu.mdnsim.nodes.NodeType;


public interface MessageBusClient {
	
	/**
	 * 
	 * Configure the MessageBusClient. After this method is called, the message
	 * bus client is expected to be ready for connecting to Master. This method
	 * just equips the MessageBusClient with fundamental configuration.
	 * 
	 * @throws MessageBusException 
	 */
	public void config() throws MessageBusException;
	
	/**
	 * 
	 * Connect the MessageBusClient to the master.
	 * 
	 * @throws MessageBusException
	 */
	public void connect() throws MessageBusException;
	
	
	/**
	 * 
	 * Send the message to the destination resource
	 * 
	 * @param fromPath The resource of the sender
	 * @param dstURI The destination URI
	 * @param method The REST method at the receiver
	 * @param msg The message to be sent
	 * 
	 * @throws MessageBusException
	 */
	public void send(String fromPath, String dstURI, String method, Message msg)
			throws MessageBusException;
	
	
	public Message request(String fromPath, String dstURI, String method, Message msg)
			throws MessageBusException;
	
	
	/**
	 * 
	 * Send the message to the master
	 * 
	 * @param fromPath The resource of the sender
	 * @param dstPath The resource of the master
	 * @param method The REST method at the master
	 * @param msg The message to be sent
	 * 
	 * @throws MessageBusException
	 */
	public void sendToMaster(String fromPath, String dstPath, String method, Message msg)
			throws MessageBusException;
	

	/**
	 * 
	 * Add a method listener.
	 * 
	 * @param resource The name of resource
	 * @param method The RESTful method for this listener
	 * @param object The object whose method is innovated upon the message
	 * @param objectMethod The name of method to be innovated
	 * 
	 * @throws MessageBusException The MessageBusException is thrown if it fails
	 * to add new method listener.
	 */
	public void addMethodListener(String resource, String method, Object object,
			String objectMethod) throws MessageBusException;
	
	
	/**
	 * 
	 * Obtain the URI in the domain
	 * 
	 * @return
	 */
	public String getURI();
	
	/**
	 * 
	 * Test if the client has been connected to the node
	 * 
	 * @return
	 */
	public boolean isConnected();
	
}
