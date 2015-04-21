package edu.cmu.mdnsim.topology;

class Node {

	String name;
	String type;
	
	Node(String name, String type) {
		this.name = name;
		this.type = type;
	}
	
	public int hashCode() {
		return this.name.hashCode();
	}
	
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (!(o instanceof Node)) {
			return false;
		}
		if (o == this) {
			return true;
		}
		Node that = (Node)o;
		if (name.equals(that.name)) {
			return true;
		} else {
			return false;
		}
	}
	
	public String toString() {
		return this.name;
	}
	
}
