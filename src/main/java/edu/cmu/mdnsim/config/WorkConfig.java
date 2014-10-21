package edu.cmu.mdnsim.config;

import java.util.LinkedList;
import java.util.List;

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
}
