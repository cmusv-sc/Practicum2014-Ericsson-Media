/**
 * 
 */
package edu.cmu.mdnsim.reporting;

/**
 * Metrics used to display tool tip when user hovers over an Edge
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class EdgeMetrics {

	public String averagePacketLoss; // in packets per sec
	public String currentPacketLoss; // in packets per sec
	public String averageTransferRate; // in bytes per sec
	public String currentTransferRate; // in bytes per sec
	public String streamStatus;

	public EdgeMetrics(){
		this.averagePacketLoss = "";
		this.currentPacketLoss = "";
		this.averageTransferRate = "";
		this.currentTransferRate = "";
		this.streamStatus = "NA"; //Not Available
	}
}
