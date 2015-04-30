package edu.cmu.mdnsim.reporting.graph;

import java.util.HashMap;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.util.HtmlTags;

public class Node{

	public static final String SRC_RGB = "rgb(0,204,0)";
	public static final String SINK_RGB = "rgb(0,204,204)";
	public static final String PROC_RGB = "rgb(204,204,0)";
	public static final String RELAY_RGB = "rgb(204,0,204)";
	public static final String TRANS_RGB = "rgb(102, 102, 0)";

	public static final String SRC_MSG = "This is a Source Node";
	public static final String SINK_MSG = "This is a Sink Node";
	public static final String PROC_MSG = "This is a Processing Node";
	public static final String RELAY_MSG = "This is a Relay Node";
	public static final String TRANS_MSG = "This is a Transcoding Node";

	public static final int NODE_SIZE_IN_GRAPH = 6;

	/**
	 * Unique value identifying the node
	 */
	public String id;
	
	public final String label;
	
	/**
	 * X location of the node in graph 
	 */
	public double x;
	/**
	 * Y location of the node in graph 
	 */
	public double y;
	/**
	 * Color of the node - specify in format "rgb(0,204,0)"
	 */
	public String color;
	/**
	 * Specifies size of the node (from 1 to 6). Not tried with large values (>6).
	 */
	public int size;

	/**
	 * Key = Stream Id, Value = Metrics for that stream
	 */
	private Map<String, NodeMetrics> streamMetricsMap = new HashMap<String, NodeMetrics>();
	/**
	 * The type of node - Source/Sink/Processing/Relay etc.
	 */
	public String nodeType;
	public Node(String id, String nodeType, String color,  int size){
		this.id = id;
		label = id;
		this.x = -1;
		this.y = -1;
		this.color = color;
		this.size = size;
		this.nodeType = nodeType;
	}

		
	@Override
	public int hashCode(){
		int res = 17;
		res = res*31 + this.id.hashCode();
		return res;
	}
	
	@Override
	public boolean equals(Object other){
		if (other == null) {
			return false;
		}
		
		if(!(other instanceof Node))
			return false;
		if(this == other)
			return true;
		
		Node otherNode = (Node)other;
		return this.id.equals(otherNode.id);
	}
	/**
	 * Updates the status of given stream in Tooltip table shown on hover of node
	 * @param streamId
	 * @param eventType
	 */
	public void updateToolTip(String streamId, EventType eventType, String cpuUsage, String memUsage) {
		NodeMetrics nodeMetrics = this.streamMetricsMap.get(streamId);
		if(nodeMetrics == null){
			nodeMetrics = new NodeMetrics();
			this.streamMetricsMap.put(streamId, nodeMetrics);
		}
		nodeMetrics.streamStatus = eventType.toString();
		nodeMetrics.cpuUsage = cpuUsage;
		nodeMetrics.memUsage = memUsage;

	}
	/**
	 * Updates the latency (for the given stream) in Tooltip table shown on hover of node 
	 * @param streamId
	 * @param latency
	 */
	public void updateToolTip(String streamId, long latency) {
		NodeMetrics nodeMetrics = this.streamMetricsMap.get(streamId);
		if(nodeMetrics == null){
			nodeMetrics = new NodeMetrics();
			this.streamMetricsMap.put(streamId, nodeMetrics);
		}
		nodeMetrics.latency = String.valueOf(latency);

	}

}
