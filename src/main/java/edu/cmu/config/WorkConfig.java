package edu.cmu.config;

import java.util.LinkedList;
import java.util.List;

public class WorkConfig {

	private List<StreamConfig> streamConfigList = new LinkedList<StreamConfig>();

	public List<StreamConfig> getStreamConfigList() {
		return streamConfigList;
	}

	public void setStreamConfigList(List<StreamConfig> streamConfigList) {
		this.streamConfigList = streamConfigList;
	}
	
	public void addStreamConfig(StreamConfig streamConfig){
		this.streamConfigList.add(streamConfig);
	}
}
