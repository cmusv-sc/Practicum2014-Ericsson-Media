package edu.cmu.mdnsim.nodes;
/**
 *  
 *
 */
public interface PortBindable {
	
	public static final int RETRY_CREATING_SOCKET_NUMBER = 3;
	/**
	 * Creates a DatagramSocket and binds it to any available port
	 * The flowId and the DatagramSocket are added to a 
	 * HashMap<flowId, DatagramSocket> in the MdnSinkNode object
	 * 
	 * @param flowId
	 * @return port number to which the DatagramSocket is bound to
	 * -1 if DatagramSocket creation failed
	 * 0 if DatagramSocket is created but is not bound to any port
	 */

	public abstract int bindAvailablePortToFlow(String flowId);
}
