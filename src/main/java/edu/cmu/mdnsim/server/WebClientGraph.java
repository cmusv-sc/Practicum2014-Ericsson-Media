package edu.cmu.mdnsim.server;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
/**
 * Represents the actual state of the graph displayed in the web client. 
 * Should not be used for JSON conversion.
 * There needs to be only single object of this class.
 * 
 * The class internally uses ConcurrentHashMap for nodes and edges, 
 * 	but none of the add/remove methods provided by this class are synchronized 
 * 	=> Thread safety is same as guaranteed by ConcurrentHashMap  
 * @author CMU-SV Ericsson Media Team
 */
public class WebClientGraph {
	/**
	 * Represents the Node in the graph displayed in web client
	 */
	public class Node{
		/**
		 * Unique value identifying the node
		 */
		public String id;
		/**
		 * This remains fixed i.e. always visible to user
		 */
		public String label;
		/**
		 * X location of the node in graph - value should be between 0 and 1
		 */
		public double x;
		/**
		 * Y location of the node in graph - value should be between 0 and 1
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
		
		public Node(String id, String label, double x, double y, String color, int size, String tag){
			this.id = id;
			this.label = label;
			this.x = x;
			this.y = y;
			this.color = color;
			this.size = size;
			this.tag = tag;
		}		
	}
	
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
		public Edge(String id, String source, String target, String type, String tag, String edgeColor, int size){
			this.id = id;
			this.source = source;
			this.target = target;
			this.type = type;
			this.tag = tag;
			this.color = edgeColor;
			this.size = size;
		}
	}
	
	public class NodeLocation{
		public Double x;
		public Double y;
		public NodeLocation(Double x, Double y){
			this.x = x; this.y = y;
		}
		@Override
		public int hashCode(){
			int res = 17;
			res = res*31 + new Double(x).hashCode();
			res = res*31 + new Double(y).hashCode();
			return res;
		}
		@Override
		public boolean equals(Object other){
			if(this == other)
				return true;
			if(!(other instanceof NodeLocation))
				return false;
			NodeLocation nl = (NodeLocation)other;
			return this.x.equals(nl.x) && this.y.equals(nl.y);
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
	 * Used to ensure that each node gets different location
	 */
	private Set<NodeLocation> nodeLocations ;
	/**
	 * Private constructor to ensure that there is only one object of the Graph
	 */
	private WebClientGraph(){
		//TODO: Specify appropriate load factor, concurrency level for the maps 
		nodesMap = new ConcurrentHashMap<String,Node>();
		edgesMap = new ConcurrentHashMap<String,Edge>();
		nodeLocations = new HashSet<NodeLocation>();
	};	
	public final static WebClientGraph INSTANCE = new WebClientGraph();
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
	 * 	which can be easily converted JSON and parsed by the client code (javascript code) 
	 * @return
	 */
	public WebClientUpdateMessage getUpdateMessage(){
		WebClientUpdateMessage msg = new WebClientUpdateMessage();
		msg.setNodes(this.nodesMap.values());
		msg.setEdges(this.edgesMap.values());
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
	
	public NodeLocation getNewNodeLocation(){
		NodeLocation nl = new NodeLocation((Math.random() * 100),(Math.random() * 100));		
		while(this.nodeLocations.contains(nl)){
			nl = new NodeLocation((Math.random() * 100),(Math.random() * 100));
		}
		this.nodeLocations.add(nl);
		return nl;
	}
}
