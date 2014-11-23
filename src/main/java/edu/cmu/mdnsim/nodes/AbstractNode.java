package edu.cmu.mdnsim.nodes;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.api.rest.PUT;
import com.ericsson.research.warp.api.rest.Path;
import com.ericsson.research.warp.api.rest.PathParam;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;

public abstract class AbstractNode {
	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.node");
	protected MessageBusClient msgBusClient;
	
	String nodeId;
	
	InetAddress hostAddr;
	
	private boolean registered = false;

	protected Map<String, DatagramSocket> streamIdToSocketMap = new HashMap<String, DatagramSocket>();
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
	public static final int MAX_WAITING_TIME_IN_MILLISECOND = 1000;
	
	public static final int INTERVAL_IN_MILLISECOND = 1000;

	private static final int RETRY_CREATING_SOCKET_NUMBER = 3;
	
	
	/**
	 * Used for reporting purposes. 
	 * Key = FlowId, Value = UpStreamNodeId
	 *//*
	Map<String,String> upStreamNodes = new HashMap<String,String>();
	*//**
	 * Used for reporting purposes.
	 * Key = FlowId, Value = DownStreamNodeId
	 *//*
	Map<String,String> downStreamNodes = new HashMap<String,String>();*/
	
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

	/**
	 * 
	 * This method is to start a stream.	 
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param flow
	 */
	public abstract void executeTask(Message request, Stream stream);
	
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
	
	/**
	 * 
	 * This method is used kill all threads that run NodeRunnable, clean up 
	 * resources(MethodListners as well!).
	 * 
	 */
	public abstract void cleanUp();

	/**
	 * This function is used by Nodes which need to receive data.
	 * Retrieves port number associated with the stream.
	 * If none exists creates a new UDP socket and returns its local port.
	 * It also stores the UDP socket in streamIdToSocketMap variable.	  
	 * @param streamId
	 * @return -1 if socket is not created successfully
	 */
	public int getAvailablePort(String streamId) {

		if (streamIdToSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamIdToSocketMap.get(streamId).getPort();
		} else {

			DatagramSocket udpSocket = null;
			for(int i = 0; i < RETRY_CREATING_SOCKET_NUMBER; i++){
				try {
					udpSocket = new DatagramSocket(0, getHostAddr());
				} catch (SocketException e) {
					if (ClusterConfig.DEBUG) {
						System.out.println("Failed" + (i + 1) + "times to bind a port to a socket");
					}
					e.printStackTrace();
					continue;
				}
				break;
			}

			if(udpSocket == null){
				return -1;
			}

			streamIdToSocketMap.put(streamId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}
	/**
	 * The from property of message should contain flow id at the end
	 * @param request
	 * @return
	 */
	protected String getFlowId(Message request) {
		String flowId = request.getFrom().toString();
		flowId = flowId.substring(flowId.lastIndexOf('/')+1);
		logger.debug("[RELAY] Flow Id: " + flowId);
		return flowId;
	}	
}
