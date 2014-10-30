package edu.cmu.mdnsim.config;
import java.util.List;

public class StreamConfig {

	private String id;
	private int size;
	private List<NodeConfig> flow;
	
	public String getId() {
		return id;
	}
	public int getSize() {
		return size;
	}
	public void setSize(int size) {
		this.size = size;
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