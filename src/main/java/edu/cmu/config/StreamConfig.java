package edu.cmu.config;
import java.util.List;

public class StreamConfig {

	private String id;
	private List<NodeConfig> flow;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public List<NodeConfig> getFlow() {
		return flow;
	}
	public void setFlow(List<NodeConfig> flow) {
		this.flow = flow;
	}
}
