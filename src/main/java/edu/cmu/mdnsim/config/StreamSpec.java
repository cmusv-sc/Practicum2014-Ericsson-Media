package edu.cmu.mdnsim.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class StreamSpec extends MbMessage {
	public String StreamId;
	public String DataSize;
	public String ByteRate;
	
	/**
	 * The flow list is ordered from sink -> middle nodes -> source
	 * The first Map in the flow is assumed to be the sink
	 */
	public ArrayList<HashMap<String, String>> Flow;
	
	/**
	 * This method is a helper method to retrieve URI of the sink node in the flow.
	 * @return The URI of sink node.
	 * @throws MessageBusException
	 */
	public String findSinkNodeURI() throws MessageBusException {
		Map<String, String>sinkNodeMap = Flow.get(0);
		if (sinkNodeMap.get("DownstreamId") != null) {
			throw new MessageBusException("StreamSpec is mis-formatted. "
					+ "The first node map is not for sink node.");
		} else if (sinkNodeMap.get("NodeUri") == null) {
			throw new MessageBusException("StreamSpec is mis-formatted. "
					+ "The first node map doesn't contain field NodeUri");
		} else {
			return sinkNodeMap.get("NodeUri");
		}
	}
}
