/**
 * 
 */
package edu.cmu.messagebus.message;

/**
 * Represents sets of nodes and edges as required by the web client.
 */
public class WebClientUpdateMessage {

	public class Node{
		public String id;
		public String label;
		public double x;
		public double y;
		public String color;
		public int size;
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
		public String id;
		public String source;
		public String target;
		public String type;
		public Edge(String id, String source, String target, String type){
			this.id = id;
			this.source = source;
			this.target = target;
			this.type = type;
		}
	}
	
	private Node[] nodes;
	private Edge[] edges;
	
	public Node[] getNodes() {
		return nodes;
	}
	public Edge[] getEdges() {
		return edges;
	}
	
	public void setNodes(Node[] nodes) {
		this.nodes = nodes;
	}
	
	public void setEdges(Edge[] edges) {
		this.edges = edges;
	}
	
	
}
