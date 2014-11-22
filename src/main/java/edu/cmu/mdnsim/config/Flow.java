package edu.cmu.mdnsim.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class Flow extends MbMessage {
	
	public static final String NODE_ID = "NodeId";
	public static final String NODE_URI = "NodeUri";
	public static final String NODE_TYPE = "NodeType";
	public static final String UPSTREAM_ID = "UpstreamId";
	public static final String UPSTREAM_URI = "UpstreamUri";
	public static final String DOWNSTREAM_ID = "DownstreamId";
	public static final String DOWNSTREAM_URI = "DownstreamUri";
	public static final String RECEIVER_IP_PORT = "ReceiverIpPort";
	public static final String PROCESSING_LOOP = "ProcessingLoop";
	public static final String PROCESSING_MEMORY = "ProcessingMemory";
	
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

	public String getSinkNodeURI() {
		Map<String, String>sinkNodeMap = nodeList.get(0);
		return sinkNodeMap.get(NODE_URI);
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
	 * This overloaded type takes the streamId as an argument
	 * @return String (FlowId)
	 */
	public String generateFlowId(String streamId) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(streamId);
		for (Map<String, String>nodeMap : nodeList) {
			sb.append("-");
			sb.append(nodeMap.get(Flow.NODE_ID));
		}
		this.flowId = sb.toString();
		return flowId;
	}
	
	/**
	 * Build and return the FlowId for the current flow and stream
	 * The FlowId is the concatenation of the streamId followed by 
	 * all NodeId's from sink to source
	 * This overloaded type gets the streamId from its member variable
	 * @return String (FlowId)
	 */
	public String generateFlowId() {
		
		StringBuilder sb = new StringBuilder();
		sb.append(streamId);
		for (Map<String, String>nodeMap : nodeList) {
			sb.append("-");
			sb.append(nodeMap.get(Flow.NODE_ID));
		}
		flowId = sb.toString();
		return flowId;
	}
	
	/**
	 * Receives the nodeId and the nodeUri of a node and 
	 * updates the flow with the Uri in the nodeMap of the 
	 * flow
	 * @param nodeId
	 * @param nodeUri
	 */
	public void updateFlowWithNodeUri(String nodeId, String nodeUri) {		
		for (Map<String, String>nodeMap : this.getNodeList()) {
//			System.out.println("Node Map before updating with "+nodeId+" "+nodeUri);
//			System.out.println(nodeMap.toString());
			if (nodeId.equals(nodeMap.get(Flow.NODE_ID))) {
				nodeMap.put(Flow.NODE_URI, nodeUri);
			}
			if (nodeId.equals(nodeMap.get(Flow.DOWNSTREAM_ID))) {
				nodeMap.put(Flow.DOWNSTREAM_URI, nodeUri);
			}
			if (nodeId.equals(nodeMap.get(Flow.UPSTREAM_ID))) {
				nodeMap.put(Flow.UPSTREAM_URI, nodeUri);
			}
//			System.out.println("Node Map after updating ");
//			System.out.println(nodeMap.toString());
		}
	}
	
	/**
	 * Updates each NodeMap in the Flow with the Id of the DownstreamId.
	 * The DownstreamId is the Id of the Node that is next in the list 
	 * of NodeMap's in the Flow
	 */
	public void updateFlowWithDownstreamIds() {
		int i = 0;
		String downStreamId = "";
		for (Map<String, String>nodeMap : this.getNodeList()) {
//			System.out.println("Before Downstream update "+nodeMap.toString());
			if (i == 0) {
				downStreamId = nodeMap.get(Flow.NODE_ID);
			} else {
				nodeMap.put("DownstreamId", downStreamId);
				downStreamId = nodeMap.get(Flow.NODE_ID);
			}
			i++;
//			System.out.println("After Downstream update "+nodeMap.toString());
		}
	}
	
	/**
	 * Tests and verifies whether a flow can be started based on whether all the upstream 
	 * and downstream NodeId's and Uri's are set
	 * @return true, if the all the nodes in the flow are up and the flow can be started
	 * false, if any node in the flow is still not up
	 */
	public boolean canRun() {
		int i = 0;
		int size = getNodeList().size();
		
		for (Map<String, String>nodeMap : getNodeList()) {
			// For all nodes check for the NodeUri
			if (!nodeMap.containsKey(Flow.NODE_URI)) {
				System.out.println("ALL: NodeUri not present in "+nodeMap.toString());
				return false;
			}
			if (i == 0) {
				// Sink Node: Check only for UpstreamUri
				if (!nodeMap.containsKey(Flow.UPSTREAM_URI)) {
					System.out.println("SINK: UpstreamUri not present in "+nodeMap.toString());
					return false;
				}
			} else if (i < size-1) {
				// Intermediate nodes: Check for DownstreamUri and UpstreamUri
				if (!nodeMap.containsKey(Flow.DOWNSTREAM_URI)) {
					System.out.println("INTER: DownstreamUri not present in "+nodeMap.toString());
					return false;
				}
				if (!nodeMap.containsKey(Flow.UPSTREAM_URI)) {
					System.out.println("INTER: UpstreamUri not present in "+nodeMap.toString());
					return false;
				}
			} else {
				// Source Node: Check only for for DownstreamUri
				if (!nodeMap.containsKey(Flow.DOWNSTREAM_URI)) {
					System.out.println("SRC: DownstreamUri not present in "+nodeMap.toString());
					return false;
				}
			}
			i++;
		}
		
		return true;
	}
	
	/**
	 * Validates the order of nodes in the NodeList of the flow
	 * @return true, if the NodeList in the flow is ordered properly from 
	 * 				 sink to source
	 * false, if the Flow is not ordered from sink to source
	 */
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