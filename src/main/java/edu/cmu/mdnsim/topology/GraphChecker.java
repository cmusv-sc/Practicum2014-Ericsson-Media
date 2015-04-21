package edu.cmu.mdnsim.topology;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;

public class GraphChecker {

	private Digraph<Node> G;
	
	public GraphChecker(WorkConfig wc) {
		G = new Digraph<Node>();
		
		Map<String, Node> nodes = new HashMap<String, Node>();
		
		int counter = 0;
		try {
			for (Stream stream : wc.getStreamList()){
				for (Flow flow : stream.getFlowList()){
					for (Map<String, String> node : flow.getNodeList()) {
						if (!nodes.containsKey(node.get(Flow.NODE_ID))) {
							nodes.put(node.get(Flow.NODE_ID), new Node(node.get(Flow.NODE_ID), node.get(Flow.NODE_TYPE)));
						}
						
						
						
						
						if (!node.get(Flow.UPSTREAM_ID).equals("NULL")) {
							if (!nodes.containsKey(node.get(Flow.UPSTREAM_ID))) {
								nodes.put(node.get(Flow.UPSTREAM_ID), new Node(node.get(Flow.UPSTREAM_ID), flow.findNodeMap(node.get(Flow.UPSTREAM_ID)).get(Flow.NODE_TYPE)));
							}
							G.addEdge(nodes.get(node.get(Flow.UPSTREAM_ID)), nodes.get(node.get(Flow.NODE_ID)));
						} else {
							System.out.println(counter++);
						}
					}
					
				}
			}
			System.out.println(G);
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		
//		System.out.println("GraphChecker.constructor: called");
		
		
	}
	
	public CheckerResult validate() {
		
		for (Node v : G.vertices()) {
			
			if (v.type.equals("SourceNode")) {
				if (G.toV(v).iterator().hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of SourceNode "
							+ "shouldn't have upstream.", v.name, v.type));
				}
			} else if (v.type.equals("SinkNode")) {
				if (G.fromV(v).iterator().hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of SinkNode "
							+ "shouldn't have downstream.", v.name, v.type));
				}
				Iterator<Node> it = G.toV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of SinkNode "
							+ "should have at leaset one upstream.", v.name, v.type));
				}
				
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of SinkNode "
							+ "should only have one upstream.", v.name, v.type));
				}
				
			} else if (v.type.equals("ProcessingNode")) {
				Iterator<Node> it = G.fromV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of ProcessingNode "
							+ "must have only one downStream.", v.name, v.type));
				}
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of ProcessingNode "
							+ "must have only one downStream.", v.name, v.type));
				}
				it = G.toV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of ProcessingNode "
							+ "must have only one upStream.", v.name, v.type));
				}
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of ProcessingNode "
							+ "must have only one upStream.", v.name, v.type));
				}
				
			} else if (v.type.equals("RelayNode")) {
				Iterator<Node> it = G.fromV(v).iterator();
				it = G.toV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of RelayNode "
							+ "must have only one upStream.", v.name, v.type));
				}
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of RelayNode "
							+ "must have only one upStream.", v.name, v.type));
				}
			} else if (v.type.equals("TranscodingNode")) {
				Iterator<Node> it = G.fromV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of TranscodingNode "
							+ "must have only one downStream.", v.name, v.type));
				}
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of TranscodingNode "
							+ "must have only one downStream.", v.name, v.type));
				}
				it = G.toV(v).iterator();
				if (!it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of TranscodingNode "
							+ "must have only one upStream.", v.name, v.type));
				}
				it.next();
				if (it.hasNext()) {
					return new CheckerResult(v.name, String.format("node[name:%s, type:%s] as type of TranscodingNode "
							+ "must have only one upStream.", v.name, v.type));
				}
			} else {
				return new CheckerResult(v.name, String.format("%s of node[name:%s, type:%s] has invalid type.", v.name, v.name, v.type));
			}
		}
		
		
		
		return new CheckerResult();
	}
	
}
