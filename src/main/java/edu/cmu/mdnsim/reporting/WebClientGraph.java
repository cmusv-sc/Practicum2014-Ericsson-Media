package edu.cmu.mdnsim.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
import edu.cmu.util.HtmlTags;
/**
 * Represents the actual state of the graph displayed in the web client. 
 * Should not be used for JSON conversion.
 * There needs to be only single object of this class.
 * 
 * The class internally uses ConcurrentHashMap for nodes and edges, 
 * 	but none of the add/remove methods provided by this class are synchronized 
 * 	=> Thread safety is same as guaranteed by ConcurrentHashMap  
 * 
 * @author Jigar Patel
 * @author Jeremy Fu
 * @author Vinay Kumar Vavili
 */
public class WebClientGraph {

	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.webclientgraph");
	/**
	 * Represents the Node in the graph displayed in web client
	 */
	public class Node{

		public static final String SRC_RGB = "rgb(0,204,0)";
		public static final String SINK_RGB = "rgb(0,204,204)";
		public static final String PROC_RGB = "rgb(204,204,0)";
		public static final String RELAY_RGB = "rgb(204,0,204)";

		public static final String SRC_MSG = "This is a Source Node";
		public static final String SINK_MSG = "This is a Sink Node";
		public static final String PROC_MSG = "This is a Processing Node";
		public static final String RELAY_MSG = "This is a Relay Node";

		public static final int NODE_SIZE_IN_GRAPH = 6;

		/**
		 * Unique value identifying the node
		 */
		public String id;
		/**
		 * This remains fixed i.e. always visible to user
		 */
		public String label;
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
		 * Used to display tooltip on hover event. It can have html tags.
		 */
		public String tag;
		/**
		 * List of downstream nodes
		 */
		private List<Node> children;
		/**
		 * Key = Stream Id, Value = Metrics for that stream
		 */
		private Map<String, NodeMetrics> streamMetricsMap = new HashMap<String, NodeMetrics>();
		/**
		 * The type of node - Source/Sink/Processing/Relay etc.
		 */
		public String nodeType;
		public Node(String id, String label, String nodeType, String color,  int size, String tag){
			this(id,label,nodeType, color,size,tag,-1,-1);
		}

		public Node(String id, String label, String nodeType, String color, int size, String tag,
				double x, double y){
			this.id = id;
			this.label = label;
			this.x = x;
			this.y = y;
			this.color = color;
			this.size = size;
			this.tag = tag;
			this.nodeType = nodeType;
			this.children = new ArrayList<Node>();
		}

		/**
		 * Adds a new node to the children. 
		 * No validations done => Duplicates will be added multiple times.
		 * @param childNode
		 */
		public void addChild(Node childNode) {
			//if(!this.children.contains(childNode))
			this.children.add(childNode);
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
			if(!(other instanceof Node))
				return false;
			Node otherNode = (Node)other;
			return this.id.equals(otherNode.id);
		}
		/**
		 * Updates the status of given stream in Tooltip table shown on hover of node
		 * @param streamId
		 * @param eventType
		 */
		public void updateToolTip(String streamId, EventType eventType) {
			NodeMetrics nodeMetrics = this.streamMetricsMap.get(streamId);
			if(nodeMetrics == null){
				nodeMetrics = new NodeMetrics();
				this.streamMetricsMap.put(streamId, nodeMetrics);
			}
			nodeMetrics.streamStatus = eventType.toString();
			this.tag = this.buildTagHtml();
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
			this.tag = this.buildTagHtml();
		}
		/**
		 * Builds HTML table to be shown on hover of node 
		 * @return String containing full HTML table element
		 */
		private String buildTagHtml(){
			StringBuilder sb = new StringBuilder();
			sb.append(HtmlTags.TABLE_BEGIN);
			sb.append(generateHeaderRow());
			for(Map.Entry<String, NodeMetrics> entry : streamMetricsMap.entrySet()){
				sb.append(HtmlTags.TR_BEGIN);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getKey()); //Stream Id
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().streamStatus);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				if(entry.getValue().latency != null){
					sb.append(entry.getValue().latency);
				}
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TR_END);
			}
			sb.append(HtmlTags.TABLE_END);
			return sb.toString();			
		}
		/**
		 * Generates Header Row for tool tip table 
		 * @return String containing HTML TR element 
		 */
		private String generateHeaderRow() {
			StringBuilder sb = new StringBuilder();
			sb.append(HtmlTags.TR_BEGIN);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Stream");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Status");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Latency");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TR_END);
			return sb.toString();
		}
	}
	/**
	 * Represents teh Edge between two nodes shown in graph
	 * @author Jigar
	 *
	 */
	public class Edge{
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
		 * Type of edge - can be used to draw different types of edges.
		 * The value specified here can be used in javascript to modify the way edge is rendered.
		 */
		public String type;
		/**
		 * Used to display tooltip when mouse hovered over the edge. Can have html tags.
		 */
		public String tag;
		/**
		 * Indicates the stream status
		 */
		public String color;
		/**
		 * size of edge in numbers (it is relative size)
		 */
		public int size;

		public static final String EDGE_COLOR = "rgb(84,84,84)"; //Grey

		public static final int EDGE_SIZE_IN_GRAPH = 5;
		/**
		 * Key = StreamId, Value = Metrics to be shown in tool tip for that edge
		 */
		private Map<String, EdgeMetrics> streamMetricsMap = new HashMap<String, EdgeMetrics>();

		public Edge(String id, String source, String target, String type, String tag, String edgeColor, int size){
			this.id = id;
			this.source = source;
			this.target = target;
			this.type = type;
			this.tag = tag;
			this.color = edgeColor;
			this.size = size;
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
		public void updateToolTip(String streamId, EventType eventType) {
			EdgeMetrics edgeMetrics = this.streamMetricsMap.get(streamId);
			if(edgeMetrics == null){
				edgeMetrics = new EdgeMetrics();
				this.streamMetricsMap.put(streamId, edgeMetrics);
			}
			edgeMetrics.streamStatus = eventType.toString();
			this.tag = this.buildTagHtml();
		}
		/**
		 * Updates the different metrics of given stream in Tooltip table shown on hover of edge
		 * @param streamId
		 * @param averagePacketLoss
		 * @param currentPacketLoss
		 * @param averageTransferRate
		 * @param currentTransferRate
		 */
		public void updateToolTip(String streamId, String averagePacketLoss, String currentPacketLoss,
				String averageTransferRate, String currentTransferRate) {
			EdgeMetrics edgeMetrics = this.streamMetricsMap.get(streamId);
			if(edgeMetrics == null){
				edgeMetrics = new EdgeMetrics();
				this.streamMetricsMap.put(streamId, edgeMetrics);
			}
			edgeMetrics.averagePacketLoss = averagePacketLoss;
			edgeMetrics.currentPacketLoss = currentPacketLoss;
			edgeMetrics.averageTransferRate = averageTransferRate;
			edgeMetrics.currentTransferRate = currentTransferRate;
			this.tag = this.buildTagHtml();
		}
		/**
		 * Builds HTML table to be shown on hover of edge 
		 * @return String containing full HTML table element
		 */
		private String buildTagHtml(){
			StringBuilder sb = new StringBuilder();
			sb.append(HtmlTags.TABLE_BEGIN);
			sb.append(generateHeaderRow());
			for(Map.Entry<String, EdgeMetrics> entry : streamMetricsMap.entrySet()){
				sb.append(HtmlTags.TR_BEGIN);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getKey()); //Stream Id
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().streamStatus);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().averagePacketLoss);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().currentPacketLoss);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().averageTransferRate);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TD_BEGIN);
				sb.append(entry.getValue().currentTransferRate);
				sb.append(HtmlTags.TD_END);
				sb.append(HtmlTags.TR_END);
			}
			sb.append(HtmlTags.TABLE_END);
			return sb.toString();			
		}
		/**
		 * Builds HTML table to be shown on hover of node 
		 * @return String containing full HTML table element
		 */
		private String generateHeaderRow() {
			StringBuilder sb = new StringBuilder();
			sb.append(HtmlTags.TR_BEGIN);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Stream");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Status");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Avg Packet Loss");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Current Packet Loss");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Avg Transfer Rate");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TD_BEGIN);sb.append("Current Transfer Rate");sb.append(HtmlTags.TD_END);
			sb.append(HtmlTags.TR_END);
			return sb.toString();
		}
		/**
		 * Gets Stream Status (One of {@link}edu.cmu.mdnsim.messagebus.message.EventType) values
		 * @param streamId
		 * @return String format of the EventType value
		 */
		public String getStreamStatus(String streamId) {
			return this.streamMetricsMap.get(streamId).streamStatus;
		}
	}
	/**
	 * Key = Node Id, Value = Node
	 */
	private ConcurrentHashMap<String,Node> nodesMap;
	/**
	 * Key = Edge Id, Value = Edge
	 */
	private ConcurrentHashMap<String,Edge> edgesMap;
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
		edgesMap = new ConcurrentHashMap<String,Edge>();

		//Create a virtual root node and add it to nodes Map.
		//Required for calculating node locations
		root = new Node("","Virtual Root Node","","",0,"");
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
				new Node("","",WorkConfig.SOURCE_NODE_TYPE_INPUT,Node.SRC_RGB,
						Node.NODE_SIZE_IN_GRAPH,Node.SRC_MSG));
		defaultNodeProperties.put(WorkConfig.PROC_NODE_TYPE_INPUT, 
				new Node("","",WorkConfig.PROC_NODE_TYPE_INPUT,Node.PROC_RGB,
						Node.NODE_SIZE_IN_GRAPH,Node.PROC_MSG));
		defaultNodeProperties.put(WorkConfig.RELAY_NODE_TYPE_INPUT, 
				new Node("","",WorkConfig.RELAY_NODE_TYPE_INPUT,Node.RELAY_RGB,
						Node.NODE_SIZE_IN_GRAPH,Node.RELAY_MSG));
		defaultNodeProperties.put(WorkConfig.SINK_NODE_TYPE_INPUT, 
				new Node("","",WorkConfig.SINK_NODE_TYPE_INPUT,Node.SINK_RGB,
						Node.NODE_SIZE_IN_GRAPH,Node.SINK_MSG));

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
	 * If the node already exists it it over written
	 * @param n
	 */
	public void addNode(Node n) {
		nodesMap.put(n.id, n);
	}
	/**
	 * If the edge already exists it is overwritten
	 * @param e
	 */
	public void addEdge(Edge e) {
		edgesMap.put(e.id, e);
	}
	public void removeNode(Node n){
		nodesMap.remove(n.id);
		//TODO: Check if we need to remove corresponding edges here or let the user of this class handle it?
	}
	public void removeEdge(Edge e){
		edgesMap.remove(e.id);
		//TODO: Check if we need to remove corresponding nodes or let the user of this class handle it?
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
		msg.setNodes(this.nodesMap.values());
		msg.setEdges(this.edgesMap.values());
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

		/* Add the edge to be displayed in the graph only if both end points of 
		 * the edge are up
		 */
		for (Edge e : this.edgesMap.values()) {
			String[] nodes = e.id.split("-");
			if (operationalNodes.contains(nodes[0]) && operationalNodes.contains(nodes[1]))
				operationalEdgeSet.add(e);
		}
		msg.setEdges(operationalEdgeSet);
		return msg;
	}

	/**
	 * Returns edge if available else null
	 * @param edgeId
	 * @return
	 */
	public Edge getEdge(String edgeId){
		return edgesMap.get(edgeId);
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
	public void addNode(Map<String, String> nodePropertiesMap) {
		String nodeId = nodePropertiesMap.get(Flow.NODE_ID);
		if (this.containsNode(nodeId)) {
			return;
		}
		String nodeType = nodePropertiesMap.get(Flow.NODE_TYPE);

		Node newNode = new Node(nodeId, nodeId, nodeType,
				this.defaultNodeProperties.get(nodeType).color, 
				this.defaultNodeProperties.get(nodeType).size, 
				this.defaultNodeProperties.get(nodeType).tag);
		newNode.y = this.defaultNodeProperties.get(nodeType).y;
		nodesMap.put(newNode.id, newNode);		

	}
	/**
	 * If the edge already exists it doesn't add it to the graph.
	 * Both the nodes should be added to the web client graph other wise it will behave abnormally
	 * This function will also set the children property of the nodes involved.
	 * @param e
	 */
	public void addEdge(Map<String, String> nodeMap) {
		String nodeId = nodeMap.get(Flow.NODE_ID);
		String upStreamNodeId = nodeMap.get(Flow.UPSTREAM_ID);
		String edgeId = getEdgeId(upStreamNodeId, nodeId);

		if (containsEdge(edgeId)) {
			return;
		}
		//Consider Source Node when setting children property as Source nodes will be child of Virtual root
		//But do a duplicate check for source nodes as the edge 
		// between virtual root and source nodes is not part of edgesMap
		if(!upStreamNodeId.equals("NULL") || !isChildOf(nodeId, upStreamNodeId))
			addChild(nodeId, upStreamNodeId);
		//Ignore Source nodes when adding edges to be displayed in graph
		// because it is creating issues in displaying the graph
		if(!upStreamNodeId.equals("NULL")){
			Edge newEdge = new Edge(edgeId, upStreamNodeId, 
					nodeId, "", edgeId, Edge.EDGE_COLOR, 
					Edge.EDGE_SIZE_IN_GRAPH);

			edgesMap.put(newEdge.id, newEdge);
		}
	}

	public boolean containsEdge(String edgeId) {
		return edgesMap.containsKey(edgeId);
	}

	public static String getEdgeId(String srcId, String dstId) {
		return srcId + "-" + dstId;
	}
	/**
	 * Adds the node with id = nodeId as child of node with id = parentNodeId.
	 * If the nodes are not present in the graph, does nothing.
	 * it does not check for duplicates
	 * @param nodeId
	 * @param parentNodeId
	 */
	private void addChild(String nodeId, String parentNodeId) {
		if(this.getNode(nodeId) != null){
			if(parentNodeId.equals("NULL")){				
				root.addChild(this.getNode(nodeId));
			}else{
				if(this.getNode(parentNodeId) != null)
					this.getNode(parentNodeId).addChild(this.getNode(nodeId));
			}
		}
	}
	/**
	 * Returns true if Node with id=nodeId is child of Node with id = parentNodeId 	
	 * @param nodeId
	 * @param parentNodeId
	 * @return
	 */
	private boolean isChildOf(String nodeId, String parentNodeId) {
		for(Node c : root.children){
			if(c.equals(this.getNode(nodeId)))
				return true;
		}
		return false;
	}
	/**
	 * Sets the X & Y location for all the nodes in graph based on tree concept with virtual node as root
	 * Ensure that all nodes are part of the graph 
	 * 	and their children property is set before calling this method  
	 * It will reset positions for all nodes and start fresh
	 */
	public void setLocations(){
		this.lastUsedXLocation = 0;
		setLocations(root, 0);		
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
	private void setLocations(Node n, double yLocation) {
		if(n == null) return;
		n.y = yLocation + VERTICAL_DISTANCE_BETWEEN_NODES;

		if(n.children.size() > 0){
			for(Node child : n.children)
				setLocations(child, n.y);
			if(n.children.size() == 1){
				//Place it just above the child
				n.x = n.children.get(0).x;				
			}else{
				//Place the node in middle of leftmost and rightmost children
				n.x = n.children.get(0).x + ((n.children.get(n.children.size()-1).x - n.children.get(0).x)/2); 
			}			
		}else{
			//For leaf nodes, just add fixed value to last X value
			n.x = this.lastUsedXLocation  + HORIZANTAL_DISTANCE_BETWEEN_LEAF_NODES;	
			this.lastUsedXLocation += HORIZANTAL_DISTANCE_BETWEEN_LEAF_NODES;
		}
	}
	public void updateEdge(String nodeId, String destinationNodeId, String streamId,
			String edgeColor, double averagePacketLoss, double currentPacketLoss,
			double averageTransferRate, double currentTransferRate) {
		Edge e = this.getEdge(getEdgeId(nodeId , destinationNodeId));
		if (e != null) {
			//Update the Edge only if the receiving node is not done receiving
			if(!e.getStreamStatus(streamId).equals(EventType.RECEIVE_END.toString())){
				e.color = edgeColor;
				e.updateToolTip(streamId, String.format("%.2f",averagePacketLoss), String.format("%.2f",currentPacketLoss), 
						String.format("%.2f",averageTransferRate), String.format("%.2f",currentTransferRate));
			}
		}
	}
	public void updateEdge(String nodeId, String destinationNodeId, String streamId,
			String edgeColor,EventType eventType) {
		Edge e = this.getEdge(getEdgeId(nodeId , destinationNodeId));
		if (e != null) {
			e.color = edgeColor;
			e.updateToolTip(streamId, eventType);
		}
	}
	public void updateNode(String nodeId, String streamId,
			EventType eventType) {
		Node n = this.getNode(nodeId);
		if(n != null){
			n.updateToolTip(streamId, eventType);			
		}
	}
	public void updateNode(String nodeId, String streamId,
			long latency) {
		Node n = this.getNode(nodeId);
		if(n != null){
			n.updateToolTip(streamId, latency);			
		}
	}
	public synchronized WebClientUpdateMessage resetWebClientGraph() {
		nodesMap.clear();
		edgesMap.clear();

		root = new Node("","Virtual Root Node","","",0,"");
		nodesMap.put(root.id, root);

		init();

		return new WebClientUpdateMessage();
	}

}