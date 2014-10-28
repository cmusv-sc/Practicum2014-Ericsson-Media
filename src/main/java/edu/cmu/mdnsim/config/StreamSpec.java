package edu.cmu.mdnsim.config;

import edu.cmu.mdnsim.messagebus.message.MbMessage;
import java.util.ArrayList;
import java.util.HashMap;

public class StreamSpec extends MbMessage {
	public String StreamId;
	public String DataSize;
	public String ByteRate;
	
	/**
	 * The flow list is ordered from sink -> middle nodes -> source
	 * The first Map in the flow is assumed to be the sink
	 */
	public ArrayList<HashMap<String, String>> Flow;
}
