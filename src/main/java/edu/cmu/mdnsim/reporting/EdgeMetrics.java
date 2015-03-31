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
	
	/**
	 * Average packet latency from the start of the stream in milliseconds
	 */
	public String avrEnd2EndLatency;
	
	/**
	 * Average packet latency from last report in milliseconds
	 */
	public String avrLnk2LnkLatency;
	
	public String streamStatus;

	public EdgeMetrics(String streamStatus) {
		this.streamStatus = streamStatus;
	}
	
	public EdgeMetrics() {
		super();
	}
		
	
}
