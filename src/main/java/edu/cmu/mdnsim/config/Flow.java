package edu.cmu.mdnsim.config;

import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class Flow extends MbMessage {
	
	/**
	 * The FlowMemberList is ordered from sink -> middle nodes -> source (bottom-up)
	 * The first Map in the flow is assumed to be the sink
	 */
	List<Map<String, String>> NodeList;
	String FlowId;
	String StreamId;
	String DataSize;
	String KiloBitRate;
	
	public String getFlowId() {
		return FlowId;
	}

	public void setFlowId(String flowId) {
		FlowId = flowId;
	}

	public List<Map<String, String>> getNodeList() {
		return NodeList;
	}

	public void setNodeList(List<Map<String, String>> nodeList) {
		NodeList = nodeList;
	}
	
	public String getStreamId() {
		return StreamId;
	}

	public void setStreamId(String streamId) {
		StreamId = streamId;
	}

	public String getDataSize() {
		return DataSize;
	}

	public void setDataSize(String dataSize) {
		DataSize = dataSize;
	}

	public String getKiloBitRate() {
		return KiloBitRate;
	}

	public void setKiloBitRate(String kiloBitRate) {
		KiloBitRate = kiloBitRate;
	}

	/**
	 * This method is a helper method to retrieve URI of the sink node in the flow.
	 * @return The URI of sink node.
	 * @throws MessageBusException
	 */
	public String findSinkNodeURI() throws MessageBusException {
		Map<String, String>sinkNodeMap = NodeList.get(0);
		if (sinkNodeMap.get("DownstreamId") != null) {
			throw new MessageBusException("StreamSpec is mis-formatted. "
					+ "The first node map is not for sink node.");
		} else if (sinkNodeMap.get("NodeUri") == null) {
			throw new MessageBusException("StreamSpec is mis-formatted. "
					+ "The first node map doesn't contain field NodeUri");
		} else {
			return sinkNodeMap.get("NodeUri");
		}
	}
	
	/**
	 * Find the node map according to the nodeId
	 * @param nodeId
	 * @return
	 */
	public Map<String, String> findNodeMap(String nodeId) {
		for (Map<String, String> nodeMap : NodeList) {
			if (nodeMap.get("NodeId").equals(nodeId)) {
				return nodeMap;
			}
		}
		return null;
	}
	
	/**
	 * Build and return the FlowId for the current flow and stream
	 * The FlowId is the concatenation of the streamId followed by 
	 * all NodeId's from sink to source
	 * @return String (FlowId)
	 */
	public String generateFlowId(String streamId) {
		StringBuilder sb = new StringBuilder();
		sb.append(streamId);
		for (Map<String, String>nodeMap : NodeList) {
			sb.append("-");
			sb.append(nodeMap.get("NodeId"));
		}
		this.FlowId = sb.toString();
		return this.FlowId;
	}
}
