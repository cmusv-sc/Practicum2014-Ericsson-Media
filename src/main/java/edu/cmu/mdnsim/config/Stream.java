package edu.cmu.mdnsim.config;

import java.util.List;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class Stream extends MbMessage {
	public String StreamId;
	public String DataSize;
	public String KiloBitRate;
	
	/**
	 * This is a list of Flows that are receiving, relaying, processing or sending
	 * a particular stream.
	 * The FlowList has a a list of maps called the FlowMemberList. Each 
	 * FlowMemberList is a map that identifies a single node in a flow and its
	 * properties like NodeType, NodeId, UpstreamId etc. 
	 */
	public List<Flow> FlowList;

	public List<Flow> getFlowList() {
		return FlowList;
	}

	public void setFlowList(List<Flow> flowList) {
		FlowList = flowList;
	}
}
