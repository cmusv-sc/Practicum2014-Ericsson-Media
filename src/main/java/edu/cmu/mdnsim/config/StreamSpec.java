package edu.cmu.mdnsim.config;

import edu.cmu.mdnsim.messagebus.message.MbMessage;
import java.util.ArrayList;
import java.util.HashMap;

/*
Example JSON object representing one source and sink
{
"SimId":"sim1",
"StreamSpecList":
  [
	{
	  "StreamId":"stream123",
	  "DataSize":"20000000",
	  "ByteRate":"625000",
	  "Flow":
	  [
		{
		  "NodeType":"SINK",
		  "NodeId":"tomato-sink1",
		  "UpstreamId":"orange-source1"
		},
		{
		  "NodeType":"SOURCE",
		  "NodeId":"orange-source1",
		  "UpstreamId":"NULL"
		}
	  ]
	}
  ]
}
 */
public class StreamSpec extends MbMessage {
	public String StreamId;
	public String DataSize;
	public String ByteRate;
	public ArrayList<HashMap<String, String>> Flow;
}
