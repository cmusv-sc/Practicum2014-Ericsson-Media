package edu.cmu.mdnsim.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.message.MbMessage;

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
