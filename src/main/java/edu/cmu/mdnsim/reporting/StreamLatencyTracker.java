/**
 * 
 */
package edu.cmu.mdnsim.reporting;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.cmu.util.Utility;

/**
 * Keeps track of latency between source and sink node(first and last node in flow) 
 * for each flow within stream
 * @author Jigar
 *
 */
public class StreamLatencyTracker {

	/**
	 * Start time is same for all flows => It is actually start time of stream (time of source send_start event)
	 */
	private String startTime = null;
	private Map<String, String> flowIdToEndTime;

	public StreamLatencyTracker(){
		this.flowIdToEndTime = new ConcurrentHashMap<String,String>(); 
	}
	public String getStartTime() {
		return startTime;
	}
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
	public Map<String, String> getFlowIdToEndTime() {
		return flowIdToEndTime;
	}
	public void setFlowIdToEndTime(Map<String, String> flowIdToEndTime) {
		this.flowIdToEndTime = flowIdToEndTime;
	}
	public void addNewFlowId(String flowId, String endTime){
		this.flowIdToEndTime.put(flowId, endTime);
	}
	public long getLatency(String flowId) {
		long latency = -1;
		try {
			latency = Utility.stringToMillisecondTime(this.flowIdToEndTime.get(flowId)) - 
							Utility.stringToMillisecondTime(this.startTime);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return latency;
	}	
}
