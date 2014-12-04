/**
 * 
 */
package edu.cmu.mdnsim.reporting;

/**
 * Metrics used to display tool tip when user hovers over node
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class NodeMetrics {

	public String streamStatus;
	public String latency;
	
	public NodeMetrics(){
		this.streamStatus = "NA"; //Not Available
		this.latency = "";
	}

}
