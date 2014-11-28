package edu.cmu.mdnsim.messagebus.message;

import java.util.Collection;

import edu.cmu.mdnsim.reporting.WebClientGraph.Edge;
import edu.cmu.mdnsim.reporting.WebClientGraph.Node;
/**
 * Represents sets of nodes and edges as required by the web client.
 * This class is used only for creating JSON message as required by the WebClient Node
 * @author CMU-SV Ericsson Media Team
 */
public class WebClientUpdateMessage extends MbMessage {
	
	private Collection<Node> nodes;
	private Collection<Edge> edges;
	
	public Collection<Node> getNodes() {
		return nodes;
	}
	/**
	 * it just points to the given input reference => does not do deep copy.
	 * @param nodes
	 */
	public void setNodes(Collection<Node> nodes) {
		this.nodes = nodes;
	}
	public Collection<Edge> getEdges() {
		return edges;
	}
	/**
	 * does not do a deep copy of input parameter
	 * @param edges
	 */
	public void setEdges(Collection<Edge> edges) {
		this.edges = edges;
	}
}
