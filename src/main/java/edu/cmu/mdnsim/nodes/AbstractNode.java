package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	String nodeId;
	
	InetAddress hostAddr;
	
	private boolean registered = false;
	
	
	/* This instance variable is used to control whether print out info and report to management layer which is used in unit test. */
	public boolean integratedTest = false;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
	public static final int MAX_WAITING_TIME_IN_MILLISECOND = 5000;
	
	public static final int INTERVAL_IN_MILLISECOND = 1000;
	
	/**
	 * Used for reporting purposes. 
	 * Key = FlowId, Value = UpStreamNodeId
	 */
	Map<String,String> upStreamNodes = new HashMap<String,String>();
	/**
	 * Used for reporting purposes.
	 * Key = FlowId, Value = DownStreamNodeId
	 */
	Map<String,String> downStreamNodes = new HashMap<String,String>();
	
	public AbstractNode() throws UnknownHostException {
		/* 
		 * Note: This may not be sufficient for proper DNS resolution
		 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		 */
		hostAddr = java.net.InetAddress.getLocalHost();
	}
	
	public void config(MessageBusClient msgBusClient, String nType, String nodeId) throws MessageBusException {
		this.msgBusClient = msgBusClient;
		this.nodeId = nodeId;
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "PUT", this, "executeTask");
		//TODO: The resource names and method need to be properly named 
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "POST", this, "terminateTask");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "DELETE", this, "releaseResource");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/confirm_node", "PUT", this, "setRegistered");
		
	}
	
	public void register() {
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setNodeName(getNodeId());
		req.setURI(msgBusClient.getURI()+"/"+getNodeId());
		try {
			msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public InetAddress getHostAddr() {
		return hostAddr;
	}
	
	public String getNodeId() {
		return nodeId;
	}
	
	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}	

	public synchronized void setRegistered(Message msg) {
		registered = true;
		if (ClusterConfig.DEBUG) {
			System.out.println("AbstractNode.setRegistered(): " + getNodeId() + " successfully registered");
		}
	}
	
	public synchronized boolean isRegistered() {
		return registered;
	}
	
	
	public void setUnitTest(boolean unitTest){
		this.integratedTest = unitTest;
	}

	/**
	 * 
	 * This methods is to start a stream.
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param flow
	 */
	public abstract void executeTask(Flow flow);
	
	/**
	 * 
	 * This method is to stop sending/receiving data at functional node.
	 * It is supposed send a message to inform its upstream to stop
	 * sending data as well.
	 * 
	 * @param flow
	 */
	public abstract void terminateTask(Flow flow);
	
	/**
	 * 
	 * This method is to clean up all resources related to this stream, such 
	 * as Datagram socket. It is supposed to send a message to inform its 
	 * downstream to clean up resources as well.
	 * 
	 * @param flow
	 */
	public abstract void releaseResource(Flow flow);
	/**
	 * Each NodeRunnable represents an individual Stream 
	 * It will run in a separate thread and will have dedicated reporting thread attached to it.
	 *
	 */


}
