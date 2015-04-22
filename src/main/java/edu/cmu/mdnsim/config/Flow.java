package edu.cmu.mdnsim.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

/**
 * Flow represents unique data flow path from sink to source node.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class Flow extends MbMessage {
	static Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.flow");
	public static final String NODE_ID = "NodeId";
	public static final String NODE_URI = "NodeUri";
	public static final String NODE_TYPE = "NodeType";
	public static final String UPSTREAM_ID = "UpstreamId";
	public static final String UPSTREAM_URI = "UpstreamUri";
	public static final String DOWNSTREAM_ID = "DownstreamId";
	public static final String DOWNSTREAM_URI = "DownstreamUri";
	public static final String RECEIVER_PUBLIC_IP_PORT = "ReceiverPublicIpPort";
	public static final String RECEIVER_LOCAL_IP_PORT = "ReceiverLocalIpPort";
	public static final String PROCESSING_LOOP = "ProcessingLoop";
	public static final String PROCESSING_MEMORY = "ProcessingMemory";
	public static final String SOURCE_UPSTREAM_ID = "NULL";
	public static final String TRANSCODING_ADAPTION_FACTOR = "TranscodingFactor";
	
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
	
	public String getSindNodeId() {
		Map<String, String>sinkNodeMap = nodeList.get(0);
		return sinkNodeMap.get(NODE_ID);
	}
	/**
	 * Extracts Stream Id from given flow id
	 * Assumption: FlowId is as per format specified in {@link edu.cmu.mdnsim.config.Flow#generateFlowId()}
	 * @param flowId
	 * @return null if input is null. 
	 */
	public static String extractStreamId(String flowId){
		if(flowId != null)
			return flowId.substring(0,flowId.indexOf("-"));
		return null;
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
			if (nodeId.equals(nodeMap.get(Flow.NODE_ID))) {
				nodeMap.put(Flow.NODE_URI, nodeUri);
			}
			if (nodeId.equals(nodeMap.get(Flow.DOWNSTREAM_ID))) {
				nodeMap.put(Flow.DOWNSTREAM_URI, nodeUri);
			}
			if (nodeId.equals(nodeMap.get(Flow.UPSTREAM_ID))) {
				nodeMap.put(Flow.UPSTREAM_URI, nodeUri);
			}
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
			if (i == 0) {
				downStreamId = nodeMap.get(Flow.NODE_ID);
			} else {
				nodeMap.put("DownstreamId", downStreamId);
				downStreamId = nodeMap.get(Flow.NODE_ID);
			}
			i++;
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
				logger.debug("ALL: NodeUri not present in "+nodeMap.toString());
				return false;
			}
			if (i == 0) {
				// Sink Node: Check only for UpstreamUri
				if (!nodeMap.containsKey(Flow.UPSTREAM_URI)) {
					logger.debug("SINK: UpstreamUri not present in "+nodeMap.toString());
					return false;
				}
			} else if (i < size-1) {
				// Intermediate nodes: Check for DownstreamUri and UpstreamUri
				if (!nodeMap.containsKey(Flow.DOWNSTREAM_URI)) {
					logger.debug("INTER: DownstreamUri not present in "+nodeMap.toString());
					return false;
				}
				if (!nodeMap.containsKey(Flow.UPSTREAM_URI)) {
					logger.debug("INTER: UpstreamUri not present in "+nodeMap.toString());
					return false;
				}
			} else {
				// Source Node: Check only for for DownstreamUri
				if (!nodeMap.containsKey(Flow.DOWNSTREAM_URI)) {
					logger.debug("SRC: DownstreamUri not present in "+nodeMap.toString());
					return false;
				}
			}
			i++;
		}
		
		return true;
	}
	
	/**
	 * Validates the order of nodes in the NodeList of the flow
	 * @return	true	if the NodeList in the flow is ordered properly from sink to source;
	 * 			false	if the Flow is not ordered from sink to source
	 */
	public boolean isValidFlow() {
		
//		String downStreamNodeId = null;
		String upStreamIdOfDownStreamNode = null;
		
		int i = 0;
		
		for (Map<String, String>nodeMap : getNodeList()) {
			
			if (i == 0) {
				if (nodeMap.get(Flow.DOWNSTREAM_ID) != null && !nodeMap.get(Flow.DOWNSTREAM_ID).equals("NULL")) {
					System.err.println(String.format("Flow.isValidFlow(): First node should be SinkNode and shoud not have downstream ID or downstream ID filed should be \"NULL\"\n"));
					return false;
				}
//				downStreamNodeId = nodeMap.get(Flow.NODE_ID);
				upStreamIdOfDownStreamNode = nodeMap.get(Flow.UPSTREAM_ID);
				
			} else if (i == getNodeList().size() - 1){
				if (nodeMap.get(Flow.UPSTREAM_ID) != null && !nodeMap.get(Flow.UPSTREAM_ID).equals("NULL")) {
					System.err.println(String.format("Flow.isValidFlow(): Last node[%s] should be SourceNode and shoud not have upstream ID or upstream ID field should be \"NULL\"\n", nodeMap.get(Flow.NODE_ID)));
					return false;
				}
				if (!nodeMap.get(Flow.NODE_ID).equals(upStreamIdOfDownStreamNode)) {
					System.err.println(String.format("Flow.isValidFlow():  Last node ID[%s] is not consistent with value of the %s field of its previous(i.e. downstream) node\n", nodeMap.get(Flow.NODE_ID), Flow.UPSTREAM_ID));
					return false;
				}
			} else {
				if (nodeMap.get(Flow.NODE_ID) == null || nodeMap.get(Flow.NODE_ID).equals("NULL")) {
					System.err.println(String.format("Flow.isValidFlow():  Intermediate node should have %s field and the value shouldn't be \"NULL\"\n", Flow.NODE_ID));
					return false;
				}
				if (!nodeMap.get(Flow.NODE_ID).equals(upStreamIdOfDownStreamNode)) {
					System.err.println(String.format("Flow.isValidFlow():  Intermediate node ID[%s] is not consistent with value of the %s field of its previous(i.e. downstream) node\n", nodeMap.get(Flow.NODE_ID), Flow.UPSTREAM_ID));
					return false;
				}
				upStreamIdOfDownStreamNode = nodeMap.get(Flow.UPSTREAM_ID);
//				downStreamNodeId = nodeMap.get(Flow.NODE_ID);
			}
			i++;
		}

		return true;
	}
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("FlowId : ");sb.append(this.getFlowId());sb.append("\n");
		sb.append("StreamId : ");sb.append(this.getStreamId());sb.append("\n");
		for(Map<String,String> nodes: this.getNodeList()){
			for(Map.Entry<String,String> nodeProperties : nodes.entrySet()){
				sb.append(nodeProperties.getKey());
				sb.append(nodeProperties.getValue());
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	/**
	 * Returns last node of the flow - typically sink node
	 * @param flowId in format specified by {@link Flow#generateFlowId()} 
	 * @return null if input is null.
	 */
	public static String extractLastNodeId(String flowId) {
		if(flowId != null){
			String flowIdWithoutStream = flowId.substring(flowId.indexOf("-") + 1);
			return flowIdWithoutStream.substring(0, flowIdWithoutStream.indexOf("-"));
		}
		return null;
	}
	/**
	 * Returns First Node of the flow - typically Source Node
	 * @param flowId in format specified by {@link Flow#generateFlowId()} 
	 * @return null if input is null.
	 */
	public static String extractFirstNodeId(String flowId) {
		if(flowId != null)
			return flowId.substring(flowId.lastIndexOf("-")+1);
		return null;
	}
}