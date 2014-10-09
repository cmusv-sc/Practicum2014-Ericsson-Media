package edu.cmu.nodes;

import edu.cmu.messagebus.NodeContainer;

public interface SinkNode {
	/**
	 * Creates a DatagramSocket and binds it to any available port
	 * The streamId and the DatagramSocket are added to a 
	 * HashMap<streamId, DatagramSocket> in the MdnSinkNode object
	 * 
	 * @param streamId
	 * @return port number to which the DatagramSocket is bound to
	 * -1 if DatagramSocket creation failed
	 * 0 if DatagramSocket is created but is not bound to any port
	 */
	public int bindAvailablePortToStream(String streamId);
	
	/**
	 * This function runs as a separate thread from the WarpThreadPool, 
	 * so that a client can request multiple streams at the same time.
	 * Each stream is received in a separate WarpPoolThread.
	 * Receives datagram packets from the source and reports
	 * the total time and total number of bytes received by the 
	 * sink node back to the master using the MDNSink object.
	 * @param streamId
	 * @param mdnSink
	 */
	
	public void receiveAndReport(String streamId, NodeContainer mdnSink);
	
	public String getHostAddr();
}
