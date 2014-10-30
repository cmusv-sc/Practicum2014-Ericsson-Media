package edu.cmu.mdnsim.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.message.MbMessage;

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
/**
 * The work config given by the user will have a list of stream specification,
 * called the StreamSpecList. This is a list of distinctly named streams of data.
 * A stream means a flow (sequence of nodes from sink to source) of data with a 
 * specified size and bitrate. 
 * A StreamId is unique across all flows. A StreamSpec includes a flow and additional
 * information about the stream like data size and rate
 * @author CMU-SV MDN practicum team (vinay)
 *
 */
public class WorkConfig extends MbMessage{

	private String simId;
	private List<StreamSpec> streamSpecList = new LinkedList<StreamSpec>();

	public List<StreamSpec> getStreamSpecList() {
		return streamSpecList;
	}

	public void setStreamSpecList(List<StreamSpec> streamSpecList) {
		this.streamSpecList = streamSpecList;
	}
	
	public void addStreamSpec(StreamSpec streamSpec){
		this.streamSpecList.add(streamSpec);
	}

	public String getSimId() {
		return simId;
	}

	public void setSimId(String simId) {
		this.simId = simId;
	}
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		for (StreamSpec streamSpec : this.getStreamSpecList()) {
			sb.append("StreamSpec:");
			for (Map<String, String> map : streamSpec.Flow) {
				sb.append("[nodeId" 
						+ map.get("NodeId") + "]\t[UpstreamId" + map.get("UpstreamId") 
						+ "]\t[DownstreamId" + map.get("DownstreamId") + "]");
			}
		}
		return sb.toString();
		
	}
}
