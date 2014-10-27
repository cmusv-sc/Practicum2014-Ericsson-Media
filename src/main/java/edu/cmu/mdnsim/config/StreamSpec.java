package edu.cmu.mdnsim.config;

import edu.cmu.mdnsim.messagebus.message.MbMessage;
import java.util.ArrayList;
import java.util.HashMap;

public class StreamSpec extends MbMessage {
	public String StreamId;
	public String DataSize;
	public String ByteRate;
	public ArrayList<HashMap<String, String>> Flow;
}
