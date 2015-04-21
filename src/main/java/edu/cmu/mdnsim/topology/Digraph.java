package edu.cmu.mdnsim.topology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Digraph<T> {

	private Map<T, Set<T>> upStream;
	private Map<T, Set<T>> downStream;
	
	public Digraph() {
		upStream	= new HashMap<T, Set<T>>();
		downStream	= new HashMap<T, Set<T>>();
	}
	
	public void addEdge(T from, T to) {
		if (!upStream.containsKey(from)) {
			upStream.put(from, new HashSet<T>());
			downStream.put(from, new HashSet<T>());
		}
		if (!upStream.containsKey(to)) {
			upStream.put(to, new HashSet<T>());
			downStream.put(to, new HashSet<T>());
			
		}
		
		upStream.get(to).add(from);
		downStream.get(from).add(to);
	}
	
	public Iterable<T> toV(T v) {
		return upStream.get(v);
	}
	
	public Iterable<T> fromV(T v) {
		return downStream.get(v);
	}
	
	public Iterable<T> vertices() {
		return downStream.keySet();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (T v : upStream.keySet()) {
			sb.append(v + " -> ");
			for (T w :  fromV(v)) {
				sb.append(w + ", ");
			}
			sb.append("|| <- ");
			for (T w : toV(v)) {
				sb.append(w + ", ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	
}
