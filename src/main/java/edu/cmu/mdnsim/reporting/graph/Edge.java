package edu.cmu.mdnsim.reporting.graph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.cmu.mdnsim.messagebus.message.EventType;

/**
 * Represents the Edge between two nodes shown in graph. Each edge contains at least one stream.
 * @author Jigar Patel
 * @author Geng Fu
 *
 */
public class Edge {
	/**
	 * Unique value representing Edge. Generated by combining the source and target nodes.
	 */
	public String id;
	
	/**
	 * Represents the source node id
	 */
	public String source;
	
	/**
	 * Represents the target node id
	 */
	public String target;
	
	/**
	 * Indicates the stream status
	 */
	public String color = DEFAULT_EDGE_COLOR;
	
	/**
	 * size of edge in numbers (it is relative size)
	 */
	public int size = DEFAULT_EDGE_SIZE_IN;

	public static final String DEFAULT_EDGE_COLOR = "rgb(84,84,84)"; //Grey

	public static final int DEFAULT_EDGE_SIZE_IN = 5;
	
	
	/**
	 * Each edge may represent multiple streams flowing through
	 */
	public Map<String, EdgeMetrics> streamMetricsMap = new ConcurrentHashMap<String, EdgeMetrics>();

	public Edge(String id, String source, String target){
		this.id = id;
		this.source = source;
		this.target = target;
	}
	
	
	@Override
	public int hashCode(){
		int res = 17;
		res = res*31 + this.id.hashCode();
		return res;
	}
	@Override
	public boolean equals(Object other){
		if(this == other)
			return true;
		if(!(other instanceof Edge))
			return false;
		Edge otherEdge = (Edge)other;
		return this.id.equals(otherEdge.id);
	}
	/**
	 * Updates the status of given stream in Tooltip table shown on hover of edge
	 * @param streamId
	 * @param eventType
	 */
	public void updateStreamStatus(String streamId, EventType eventType) {
		
		EdgeMetrics edgeMetrics = this.streamMetricsMap.get(streamId);
		
		if(edgeMetrics == null){
			edgeMetrics = new EdgeMetrics();
			this.streamMetricsMap.put(streamId, edgeMetrics);
		}
		edgeMetrics.streamStatus = eventType.toString();
		
	}
	/**
	 * Updates the different metrics of given stream in Tooltip table shown on hover of edge
	 * @param streamId
	 * @param averagePacketLoss
	 * @param currentPacketLoss
	 * @param averageTransferRate
	 * @param currentTransferRate
	 */
	public void updateMetrics(String streamId, String averagePacketLoss, String currentPacketLoss,
			String averageTransferRate, String currentTransferRate, String avrLnk2LnkLatency, 
			String avrEnd2EndLatency) {
		
		EdgeMetrics edgeMetrics = this.streamMetricsMap.get(streamId);
		
		if(edgeMetrics == null){
			edgeMetrics = new EdgeMetrics();
			this.streamMetricsMap.put(streamId, edgeMetrics);
		}
		
		synchronized(edgeMetrics) {
			edgeMetrics.averagePacketLoss = averagePacketLoss;
			edgeMetrics.currentPacketLoss = currentPacketLoss;
			edgeMetrics.averageTransferRate = averageTransferRate;
			edgeMetrics.currentTransferRate = currentTransferRate;
			edgeMetrics.avrLnk2LnkLatency = avrLnk2LnkLatency;
			edgeMetrics.avrEnd2EndLatency = avrEnd2EndLatency;
		}

	}

	/**
	 * Gets Stream Status (One of {@link}edu.cmu.mdnsim.messagebus.message.EventType) values
	 * @param streamId
	 * @return String format of the EventType value
	 */
	public String getStreamStatus(String streamId) {
		if(this.streamMetricsMap.get(streamId) != null)
			return this.streamMetricsMap.get(streamId).streamStatus;
		return null;
	}
}
