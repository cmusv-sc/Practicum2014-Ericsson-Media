package edu.cmu.mdnsim.config;

import java.util.LinkedList;
import java.util.List;

public class WorkConfig {

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
}
