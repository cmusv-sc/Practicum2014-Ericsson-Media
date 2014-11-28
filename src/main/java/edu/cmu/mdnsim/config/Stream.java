package edu.cmu.mdnsim.config;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class Stream extends MbMessage {
	private String streamId;
	private String dataSize;
	private String kiloBitRate;
	/**
	 * This is a list of Flows that are receiving, relaying, processing or sending
	 * a particular stream.
	 * The FlowList has a a list of maps called the FlowMemberList. Each 
	 * FlowMemberList is a map that identifies a single node in a flow and its
	 * properties like NodeType, NodeId, UpstreamId etc. 
	 */
	private List<Flow> flowList = new ArrayList<Flow>();
	
	public Stream() {
		super();
	}
	
	public Stream(String streamId, String dataSize, String kiloBitRate) {
		this.streamId = streamId;
		this.dataSize = dataSize;
		this.kiloBitRate = kiloBitRate;
	}
	
	public List<Flow> getFlowList() {
		return flowList;
	}

	
	public void setFlowList(List<Flow> flowList) {
		this.flowList = flowList;
	}
	
	public void addFlow(Flow flow) {
		flow.setStreamId(streamId);
		flow.setDataSize(dataSize);
		flow.setKiloBitRate(kiloBitRate);
		this.flowList.add(flow);
	}

	
	public String getStreamId() {
		return streamId;
	}

	
	public void setStreamId(String streamId) {
		this.streamId = streamId;
	}

	
	public String getDataSize() {
		return dataSize;
	}

	
	public void setDataSize(String dataSize) {
		this.dataSize = dataSize;
	}

	
	public String getKiloBitRate() {
		return kiloBitRate;
	}

	
	public void setKiloBitRate(String kiloBitRate) {
		this.kiloBitRate = kiloBitRate;
	}
	
	/**
	 * Test if the stream is validate.
	 * @return
	 */
	public boolean isValidStream() {
		for (Flow flow : flowList) {
			if (!flow.isValidFlow()) {
				return false;
			}
		}
		return true;
	}


	public Flow findFlow(String flowId) {
		for (Flow flow : flowList) {
			System.out.println("[DELETE-JEREMY]Stream.findFlow(): Scan flowId=" + flow.getFlowId());
			if(flow.getFlowId().equals(flowId))
				return flow;
		}
		return null;
	}
	
	/**
	 * Replace the existed flow with a new flow of same flowId.
	 * 
	 * @param flow
	 */
	public void replaceFlow(Flow flow){
		int oldFlowIdx = -1;
		for (Flow oldFlow : this.flowList) {
			if (oldFlow.getFlowId().equals(flow.getFlowId())) {
				oldFlowIdx++;
				break;
			} else {
				oldFlowIdx++;
			}
		}
		if (oldFlowIdx == -1) {
			throw new RuntimeException("Invalid flow. Cannot find the flow with flowID=" + flow.getFlowId() + " in current stream.");
		}
		this.flowList.remove(oldFlowIdx);
		this.flowList.add(flow);
	}
	
	/**
	 * Check if the stream has contained a flow with same flow ID as parameters
	 * 
	 * @param flow
	 * @return
	 */
	public boolean containsFlowID(String flowId) {
		for (Flow existedFlow : flowList) {
			if (existedFlow.equals(flowId)) {
				return true;
			}
		}
		return false;
	}
}
