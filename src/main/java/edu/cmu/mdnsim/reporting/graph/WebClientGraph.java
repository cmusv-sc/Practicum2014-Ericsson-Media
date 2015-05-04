package edu.cmu.mdnsim.reporting.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
/**
 * Represents the actual state of the graph displayed in the web client. 
 * Should not be used for JSON conversion.
 * There needs to be only single object of this class.
 * 
 * The class internally uses ConcurrentHashMap for nodes and edges, 
 * 	but none of the add/remove methods provided by this class are synchronized 
 * 	=> Thread safety is same as guaranteed by ConcurrentHashMap  
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 */
public class WebClientGraph {

	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.webclientgraph");

	/**
	 * Key = Node Id, Value = Node
	 */
	private Map<String,Node> nodesMap;
	
//	
//	/**
//	 * key: the node ID; value: the edges from the upstream
//	 */
//	private Map<Node, Set<Edge>> upstreamNodes;
	
	/**
	 * key: the node ID; value: the edges from the upstream
	 */
	private ConcurrentHashMap<Node, Set<Edge>> downstreamNodes;
	
	/**
	 * Virtual Root Node used for calculating node locations
	 */
	private Node root;
	
	
	
	private int lastUsedXLocation = 0;
	/**
	 * Key = NodeType, Value = default node class based on its type
	 */
	private Map<String,Node> defaultNodeProperties;
	/**
	 * Private constructor to ensure that there is only one object of the Graph
	 */
	private WebClientGraph(){
		//TODO: Specify appropriate load factor, concurrency level for the maps 
		nodesMap = new ConcurrentHashMap<String,Node>();
//		upstreamNodes = new ConcurrentHashMap<Node,Set<Edge>>();
		downstreamNodes = new ConcurrentHashMap<Node, Set<Edge>>();

		//Create a virtual root node and add it to nodes Map.
		//Required for calculating node locations
		root = new Node("virtual root","","",0);
		nodesMap.put(root.id, root);

		defaultNodeProperties = new HashMap<String,Node>();
		init();
	};	

	/**
	 * Whenever a new Node Type is added, make changes here to set 
	 * 	default color,size other properties of that node type
	 */
	private void init() {
		defaultNodeProperties.put(WorkConfig.SOURCE_NODE_TYPE_INPUT, 
				new Node("virtual root",WorkConfig.SOURCE_NODE_TYPE_INPUT,Node.SRC_RGB,
						Node.NODE_SIZE_IN_GRAPH));
		defaultNodeProperties.put(WorkConfig.PROC_NODE_TYPE_INPUT, 
				new Node("virtual root",WorkConfig.PROC_NODE_TYPE_INPUT,Node.PROC_RGB,
						Node.NODE_SIZE_IN_GRAPH));
		defaultNodeProperties.put(WorkConfig.RELAY_NODE_TYPE_INPUT, 
				new Node("virtual root",WorkConfig.RELAY_NODE_TYPE_INPUT,Node.RELAY_RGB,
						Node.NODE_SIZE_IN_GRAPH));
		defaultNodeProperties.put(WorkConfig.SINK_NODE_TYPE_INPUT, 
				new Node("virtual root",WorkConfig.SINK_NODE_TYPE_INPUT,Node.SINK_RGB,
						Node.NODE_SIZE_IN_GRAPH));
		defaultNodeProperties.put(WorkConfig.TRANS_NODE_TYPE_INPUT, 
				new Node("virtual root",WorkConfig.TRANS_NODE_TYPE_INPUT,Node.TRANS_RGB,
						Node.NODE_SIZE_IN_GRAPH));

	}
	public final static WebClientGraph INSTANCE = new WebClientGraph();
	/**
	 * Used for setting X location of each node
	 */
	private static final double HORIZANTAL_DISTANCE_BETWEEN_LEAF_NODES = 10;
	/**
	 * Used for setting Y location of each node
	 */
	private static final double VERTICAL_DISTANCE_BETWEEN_NODES = 10;
	

	/**
	 * Gets the node from the graph. Returns null if not present.
	 * @param nodeId
	 * @return
	 */
	public Node getNode(String nodeId){
		return nodesMap.get(nodeId);
	}

	/**
	 * Used to generate WebClient Update Message - 
	 *  which can be easily converted JSON and parsed by the client code (javascript code)
	 * This version of the over-loaded method returns the entire set of nodes and edges 
	 * even though some nodes may not be operational yet 
	 * @return
	 */
	public WebClientUpdateMessage getUpdateMessage(){
		WebClientUpdateMessage msg = new WebClientUpdateMessage();
		Set<Node> nodes = new HashSet<Node>(); 
		for (Node n : this.nodesMap.values()) {
			if (n != root) {
				nodes.add(n);
			}
		}
		msg.setNodes(nodes);
		
		Set<Edge> edges = new HashSet<Edge>();
		for (Node node : this.nodesMap.values()) {
			downstreamNodes.computeIfAbsent(node, (foo) -> new HashSet<Edge>());
			for (Edge e : downstreamNodes.get(node)) {
				if (!e.source.equals(root.id)) {
					edges.add(e);
				}
			}
		}
		msg.setEdges(edges);
		return msg;
	}
	

	/**
	 * Used to generate WebClient Update Message - 
	 *  which can be easily converted JSON and parsed by the client code (javascript code)
	 *  This version of the over-loaded method returns the set of nodes and edges which are 
	 *  operational. (i.e. registered with the master)
	 * @param operationalNodes
	 * @return
	 */
	public WebClientUpdateMessage getUpdateMessage(Set<String> operationalNodes) {
		
		WebClientUpdateMessage msg = new WebClientUpdateMessage();
		
		List<Node> operationalNodeSet = new ArrayList<Node>();
		List<Edge> operationalEdgeSet = new ArrayList<Edge>();

		for (String nodeId : operationalNodes)
			operationalNodeSet.add(this.getNode(nodeId));
		
		msg.setNodes(operationalNodeSet);
		
		for (Node v : operationalNodeSet) {
			for (Edge e : downstreamNodes.get(v)) {
				if (operationalNodes.contains(e.target)) {
					operationalEdgeSet.add(e);
				}
			}
		}

		msg.setEdges(operationalEdgeSet);
		return msg;
	}


	/**
	 * Check whether the node is contained in the graph.
	 * 
	 * @param nodeId
	 * @return
	 */
	public boolean containsNode(String nodeId) {
		return nodesMap.containsKey(nodeId);
	}

	/**
	 * If the node already exists it doesn't add the node to the graph
	 * @param n
	 */
	private void addNode(String nodeId, String nodeType) {
		
		if (this.containsNode(nodeId)) {
			return;
		}

		Node newNode = new Node(nodeId, nodeType,
				this.defaultNodeProperties.get(nodeType).color, 
				this.defaultNodeProperties.get(nodeType).size);
		
		//TODO: do we really need this?
//		newNode.y = this.defaultNodeProperties.get(nodeType).y;
		
		nodesMap.put(newNode.id, newNode);		

	}
	
	/**
	 * If the edge already exists it doesn't add it to the graph.
	 * Both the nodes should be added to the web client graph other wise it will behave abnormally
	 * This function will also set the children property of the nodes involved.
	 * @param e
	 */
	public void addEdge(String srcNodeId, String srcNodeType, String dstNodeId, String dstNodeType) {
		
		if (srcNodeId.equals(Flow.SOURCE_UPSTREAM_ID)) {
			srcNodeId = root.id;
		}
		
		if (!nodesMap.containsKey(srcNodeId)) {
			addNode(srcNodeId, srcNodeType);
		}
		
		if (!nodesMap.containsKey(dstNodeId)) {
			addNode(dstNodeId, dstNodeType);
		}
		
		Node srcNode = nodesMap.get(srcNodeId);
		Node dstNode = nodesMap.get(dstNodeId);
		
		if (!downstreamNodes.containsKey(srcNode)) {
			downstreamNodes.put(srcNode, new HashSet<Edge>());
		}
		
		if (!downstreamNodes.containsKey(dstNode)) {
			downstreamNodes.put(dstNode, new HashSet<Edge>());
		}
		
		// If the edge existed, do nothing
		for(Edge e : downstreamNodes.get(srcNode)) {
			if (e.target.equals(dstNodeId)) {
				return;
			}
		}
		
		downstreamNodes.get(srcNode).add(new Edge(getEdgeId(srcNodeId, dstNodeId), srcNodeId, dstNodeId));

	}

	public static String getEdgeId(String srcId, String dstId) {
		return srcId + "-" + dstId;
	}
	
	
	/**
	 * Sets the X & Y location for all the nodes in graph based on tree concept with virtual node as root
	 * Ensure that all nodes are part of the graph 
	 * 	and their children property is set before calling this method  
	 * It will reset positions for all nodes and start fresh
	 */
	public void setLocations(){
		this.lastUsedXLocation = 0;
		//Key = Node Id 
		//Used to ensure that node's location is set only once esp. when node is part of multiple streams like in P2P case
		Set<String> visitedNodes = new HashSet<String>();
		setLocations(root, 0, visitedNodes);		
	}
	/**
	 * Helper function which traverses the tree in DFS way setting x and y locations
	 * For Y Location, it just adds a constant to the parent's y location
	 * For X Location, uses following rules:
	 * 	1. If the node is a leaf node then set x = last Used X Location + some constant value
	 * 	2. If the node has only 1 child then place it just above that child
	 * 	3. If the node has multiple children then place it in middle of leftmost and rightmost children 
	 * @param n Node
	 * @param yLocation of the parent node
	 */
	private void setLocations(Node n, double yLocation, Set<String> visitedNodes) {
		
		if(n == null) return;
		
		Queue<Node> currLayer	= new LinkedList<Node>(),
					nextLayer	= new LinkedList<Node>();
		Set<String>   visited	= new HashSet<String>();
		
		double currLayerNodeNum = 1;
		double currLayerNodeSeq = 1;
		int    currLayerSeq     = 1;
		
		currLayer.offer(root);
		visited.add(root.id);
		
		
		while(!currLayer.isEmpty()) {
			Node tmp = currLayer.poll();
			for (Edge e : downstreamNodes.get(tmp)) {
				if (!visited.contains(e.target)) {
					nextLayer.offer(nodesMap.get(e.target));
					visited.add(e.target);
				}
			}
			tmp.x = 100 / (currLayerNodeNum + 1) * currLayerNodeSeq + (Math.random() - 0.5);
			tmp.y = 10 * currLayerSeq;
			currLayerNodeSeq++;
			if(currLayer.isEmpty()) {
				Queue<Node> tmpQ = currLayer;
				currLayer = nextLayer;
				nextLayer = tmpQ;
				nextLayer.clear();
				currLayerNodeNum = currLayer.size();
				currLayerNodeSeq = 1;
				currLayerSeq++;
			}
			
		}
		
	}
	/**
	 * Used to update the Edge Tooltip and color. This function is called for updating progress reports.
	 * @param srcId
	 * @param dstId
	 * @param streamId
	 * @param edgeColor
	 * @param averagePacketLoss
	 * @param currentPacketLoss
	 * @param averageTransferRate
	 * @param currentTransferRate
	 */
	public void updateEdge(String srcId, String dstId, String streamId,
			String edgeColor, double averagePacketLoss, double currentPacketLoss,
			double averageTransferRate, double currentTransferRate, double avrLnk2LnkLatency, 
			double avrEnd2EndLatency) {
		
		if (srcId.equals(Flow.SOURCE_UPSTREAM_ID)) {
			srcId = root.id;
		}
		
		if (nodesMap.get(srcId) == null) {
			logger.debug("updateEdge(): Try to update a edge from node[" + srcId + "] that doesn't exist in the graph.");
			return;
		}
		
		Set<Edge> downstreamEdges = downstreamNodes.computeIfAbsent(nodesMap.get(srcId), (foo)->(new HashSet<Edge>()));
		Edge e = null;
		
		for (Edge tmp : downstreamEdges) {
			if (tmp.target.equals(dstId)) {
				e = tmp;
				break;
			}
		}
		
		if (e == null) {
			throw new RuntimeException("cannot find edge from " + srcId + " to " + dstId);
		}


		String streamStatus = e.getStreamStatus(streamId);
		if(streamStatus != null){
			//Update the Edge only if the receiving node is not done receiving
			if(!streamStatus.equals(EventType.RECEIVE_END.toString())){
				e.color = edgeColor;
				e.updateMetrics(streamId, String.format("%.2f",averagePacketLoss), String.format("%.2f",currentPacketLoss), 
						String.format("%.2f",averageTransferRate), String.format("%.2f",currentTransferRate),
						String.format("%.2f", avrLnk2LnkLatency), String.format("%.2f", avrEnd2EndLatency));
			}
		}
	}
	/**
	 * Used to update Edge Tooltip and color. Called when a node starts or stops receiving data
	 * @param nodeId
	 * @param destinationNodeId
	 * @param streamId
	 * @param edgeColor
	 * @param eventType
	 */
	public void updateEdge(String nodeId, String destinationNodeId, String streamId,
			String edgeColor,EventType eventType) {
		
		Set<Edge> downstreamEdges = downstreamNodes.get(nodesMap.get(nodeId));
		Edge e = null;
		for (Edge tmp : downstreamEdges) {
			if (tmp.target.equals(destinationNodeId)) {
				e = tmp;
				break;
			}
		}
		
		if (e == null) {
			throw new RuntimeException("cannot find edge from " + nodeId + " to " + destinationNodeId);
		}
		
		e.color = edgeColor;
		e.updateStreamStatus(streamId, eventType);
		
	}
	/**
	 * Used to update Tooltip of the Node. Called when node start/stops sending/receiving data
	 * @param nodeId
	 * @param streamId
	 * @param eventType
	 */
	public void updateNode(String nodeId, String streamId,
			EventType eventType, String cpuUsage, String memUsage) {
		Node n = this.getNode(nodeId);
		if(n != null){
			synchronized(n){
				n.updateNode(streamId, eventType, cpuUsage, memUsage);
			}
		}
	}

	public synchronized WebClientUpdateMessage resetWebClientGraph() {
		
		nodesMap.clear();
		downstreamNodes.clear();

		root = new Node("virtual root","","",0);
		
		nodesMap.put(root.id, root);

		init();

		return new WebClientUpdateMessage();
	}

}
