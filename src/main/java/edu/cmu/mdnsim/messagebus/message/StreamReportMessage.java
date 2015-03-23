package edu.cmu.mdnsim.messagebus.message;

import edu.cmu.util.Utility;

/**
 * Used for reporting Stream related metrics 
 * 	like packet loss, transfer rate, start of flow, end of flow
 * To create an object if this class, use the Builder class and call its build method.
 * 	Or else use the setters
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class StreamReportMessage extends MbMessage{
	/**
	 * Flow for which report is being sent.
	 * FlowId contains StreamId.
	 * Assumption: Format of flowId is as per {@link edu.cmu.mdnsim.config.Flow#generateFlowId()}
	 */
	private String flowId;	
	/**
	 * It can be either node which is sending data to this node 
	 * or node which is receiving data from this node
	 */
	private String destinationNodeId;
	/**
	 * Indicate what event occurred which triggered sending of report message
	 */
	private EventType eventType;
	/**
	 * Indicates time when the event occurred.
	 * If event is start sending then it indicates start time.
	 * If event is done sending then it indicates end time. 
	 */
	private String eventTime; 
	/**
	 * Total Bytes transfered can be either sent or received depending on type of event
	 */
	private long totalBytesTransferred;
	
	/**
	 * This is to report CPU usage (unit: percent) of a node;
	 */
	private String cpuUsage = "N/A";
	
	/**
	 * This is to report memory usage (unit: percent) of a node;
	 */
	private String memUsage = "N/A";
	
	
	/**
	 * Following fields are used by all nodes which receive some data. 
	 */
	private double averagePacketLossRate = 0.0;
	private double currentPacketLossRate= 0.0;
	private double averageTransferRate= 0.0;
	private double currentTransferRate= 0.0;
	
	
	/**
	 * This default constructor is reserved for JSON
	 */
	private StreamReportMessage() {
		
	}

	private StreamReportMessage(Builder builder){
		super();
		this.setFlowId(builder.flowId);
		this.setEventType(builder.eventType);
		this.setDestinationNodeId(builder.destinationNodeId);
		this.setEventTime(builder.eventTime);
		this.setTotalBytesTransferred(builder.totalBytesTransferred);
		this.setAveragePacketLossRate(builder.averagePacketLossRate);
		this.setAverageTransferRate(builder.averageTransferRate);
		this.setCurrentPacketLossRate(builder.currentPacketLossRate);
		this.setCurrentTransferRate(builder.currentTransferRate);
		
		
		cpuUsage = builder.cpuUsage;
		memUsage = builder.memUsage;
		
	}
	public String getFlowId() {
		return flowId;
	}
	public void setFlowId(String flowId) {
		this.flowId = flowId;
	}
	public String getDestinationNodeId() {
		return destinationNodeId;
	}
	public void setDestinationNodeId(String destinationNodeId) {
		this.destinationNodeId = destinationNodeId;
	}
	public EventType getEventType() {
		return eventType;
	}
	public void setEventType(EventType eventType) {
		this.eventType = eventType;
	}
	public String getEventTime() {
		return eventTime;
	}
	public void setEventTime(String eventTime) {
		this.eventTime = eventTime;
	}
	public long getTotalBytesTransferred() {
		return totalBytesTransferred;
	}
	public void setTotalBytesTransferred(long totalBytesTransferred) {
		this.totalBytesTransferred = totalBytesTransferred;
	}
	public double getAveragePacketLossRate() {
		return averagePacketLossRate;
	}
	public void setAveragePacketLossRate(double averagePacketLossRate) {
		this.averagePacketLossRate = averagePacketLossRate;
	}
	public double getCurrentPacketLossRate() {
		return currentPacketLossRate;
	}
	public void setCurrentPacketLossRate(double currentPacketLossRate) {
		this.currentPacketLossRate = currentPacketLossRate;
	}
	public double getAverageTransferRate() {
		return averageTransferRate;
	}
	public void setAverageTransferRate(double averageTransferRate) {
		this.averageTransferRate = averageTransferRate;
	}
	public double getCurrentTransferRate() {
		return currentTransferRate;
	}
	public void setCurrentTransferRate(double currentTransferRate) {
		this.currentTransferRate = currentTransferRate;
	}
	
	public void setCPUUsage(String cpuUsage) {
		this.cpuUsage = cpuUsage;
	}
	
	public void setMemUsage(String memUsage) {
		this.memUsage = memUsage;
	}
	
	public String getCPUUsage() {
		return cpuUsage;
	}
	
	public String getMemUsage() {
		return memUsage;
	}
	
	/**
	 * Builder class used to build Stream Report Message
	 * Use the build method of this class to get a new StreamReportMessage object.
	 * @author Jigar Patel
	 *
	 */
	public static class Builder{
		
		private String destinationNodeId;
		private EventType eventType;
		
		private String flowId = null;	
		private String eventTime = Utility.currentTime(); 
		private long totalBytesTransferred = -1;
		private double averagePacketLossRate = -1;
		private double currentPacketLossRate = -1;
		private double averageTransferRate = -1;
		private double currentTransferRate = -1;
		
		
		private String cpuUsage;
		private String memUsage;
		
		//Required Parameters
		public Builder(EventType eventType, String destinationNodeId, String cpuUsage, String memUsage){
			this.eventType = eventType;
			this.destinationNodeId = destinationNodeId;
			this.cpuUsage = cpuUsage;
			this.memUsage = memUsage;
		}
		//Optional Parameters
		public Builder flowId(String flowId){
			this.flowId = flowId;
			return this;
		}
		public Builder eventTime(String eventTime){
			this.eventTime = eventTime;
			return this;
		}
		public Builder totalBytesTransferred(long totalBytesTransferred){
			this.totalBytesTransferred = totalBytesTransferred;
			return this;
		}
		public Builder averagePacketLossRate(double averagePacketLossRate){
			this.averagePacketLossRate = averagePacketLossRate;
			return this;
		}
		public Builder currentPacketLossRate(double currentPacketLossRate){
			this.currentPacketLossRate = currentPacketLossRate;
			return this;
		}
		public Builder averageTransferRate(double averageTransferRate){
			this.averageTransferRate = averageTransferRate;
			return this;
		}
		public Builder currentTransferRate(double currentTransferRate){
			this.currentTransferRate = currentTransferRate;
			return this;
		}
		public StreamReportMessage build(){
			return new StreamReportMessage(this);
		}
		
	}
}
