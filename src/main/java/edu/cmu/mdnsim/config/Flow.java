package edu.cmu.mdnsim.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class Flow extends MbMessage {
	
	public static final String NODE_ID = "NodeId";
	public static final String NODE_TYPE = "NodeType";
	public static final String UPSTREAM_ID = "UpstreamId";
	public static final String DOWNSTREAM_ID = "DownstreamId";
	
	/**
	 * The FlowMemberList is ordered from sink -> middle nodes -> source (bottom-up)
	 * The first Map in the flow is assumed to be the sink
	 */
	List<Map<String, String>> nodeList = new ArrayList<Map<String, String>>();
	String flowId;
	String streamId;
	String dataSize;
	String kiloBitRate;
	
	public String getFlowId() {
		return flowId;
	}

	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}

	public List<Map<String, String>> getNodeList() {
		return nodeList;
	}

	public void setNodeList(List<Map<String, String>> nodeList) {
		this.nodeList = nodeList;
	}
	
	public void addNode(Map<String, String> nodeMap) {
		this.nodeList.add(nodeMap);
	}
	
	public String getStreamId() {
		return streamId;
	}

	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}

	public String getDataSize() {
		return dataSize;
	}

	public void setDataSize(String dataSize) {
		this.dataSize = dataSize;
	}

	public String getKiloBitRate() {
		return kiloBitRate;
	}

	public void setKiloBitRate(String kiloBitRate) {
		this.kiloBitRate = kiloBitRate;
	}

	/**
	 * This method is a helper method to retrieve URI of the sink node in the flow.
	 * @return The URI of sink node.
	 * @throws MessageBusException
	 */
	public String findSinkNodeURI() throws MessageBusException {
		Map<String, String>sinkNodeMap = nodeList.get(0);
		if (sinkNodeMap.get("DownstreamId") != null) {
			throw new MessageBusException("Flow is mis-formatted. "
					+ "The first node map is not for sink node.");
		} else if (sinkNodeMap.get("NodeUri") == null) {
			throw new MessageBusException("Flow is mis-formatted. "
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
		for (Map<String, String> nodeMap : nodeList) {
			if (nodeMap.get(NODE_ID).equals(nodeId)) {
				return nodeMap;
			}
		}
		return null;
	}
	
//	public Map<String, String> findUpstreamNodeMap(String nodeId) {
//		Map<String, String> currNodeMap = findNodeMap(nodeId);
//		if (currNodeMap == null) {
//			return null;
//		}
//		
//		return null;
//	}
	
	/**
	 * Build and return the FlowId for the current flow and stream
	 * The FlowId is the concatenation of the streamId followed by 
	 * all NodeId's from sink to source
	 * @return String (FlowId)
	 */
	public String generateFlowId(String streamId) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(streamId);
		for (Map<String, String>nodeMap : nodeList) {
			sb.append("-");
			sb.append(nodeMap.get(Flow.NODE_ID));
		}
		flowId = sb.toString();
		return flowId;
	}
	
	
	public boolean isValidFlow() {
		
		String downStreamNodeId = null;
		String upStreamIdOfDownStreamNode = null;
		
		int i = 0;
		
		for (Map<String, String>nodeMap : getNodeList()) {
			
			if (i == 0) {
				if (nodeMap.get(Flow.DOWNSTREAM_ID) != null) {
					return false;
				}
				
				downStreamNodeId = nodeMap.get(Flow.NODE_ID);
				upStreamIdOfDownStreamNode = nodeMap.get(Flow.UPSTREAM_ID);
				
			} else if (i == getNodeList().size() - 1){
				if (nodeMap.get(Flow.UPSTREAM_ID) != null) {
					return false;
				}
				if (nodeMap.get(Flow.DOWNSTREAM_ID) == null ||
						!nodeMap.get(Flow.DOWNSTREAM_ID).equals(downStreamNodeId)) {
					return false;
				}
			} else {
				if (nodeMap.get(Flow.NODE_ID) == null ||
						!nodeMap.get(Flow.NODE_ID).equals(upStreamIdOfDownStreamNode)) {
					return false;
				}
				if (nodeMap.get(Flow.DOWNSTREAM_ID) == null ||
						!nodeMap.get(Flow.DOWNSTREAM_ID).equals(downStreamNodeId)) {
					return false;
				}
				upStreamIdOfDownStreamNode = nodeMap.get(Flow.UPSTREAM_ID);
				downStreamNodeId = nodeMap.get(Flow.NODE_ID);
			}
			i++;
		}

		return true;
	}
}
