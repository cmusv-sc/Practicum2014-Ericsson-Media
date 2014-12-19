package edu.cmu.mdnsim.nodes;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;

/**
 * A abstract node that defines basic behavior of concrete nodes.
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public abstract class AbstractNode {
	
	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.node");
	protected MessageBusClient msgBusClient;
	
	String nodeId;
	
	InetAddress hostAddr;
	
	private boolean registered = false;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;

	private static final int RETRY_CREATING_SOCKET_NUMBER = 3;
	public static final long NANOSECONDS_PER_SECOND = 1000000000;
	
	/**
	 * Construct a node that is using local host as the host address.
	 * 
	 * Note: Only using getLocalHost method may not be sufficient for proper DNS resolution
	 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
	 * @throws UnknownHostException when it fails to get the local Internet address
	 */
	public AbstractNode(String nodePublicIP) throws UnknownHostException {
		
		hostAddr = InetAddress.getByAddress(Utility.convertIPv4StrToByteArray(nodePublicIP));
		
	}
	
	/**
	 * Configure communication with message bus.
	 * @param msgBusClient
	 * @param nType
	 * @param nodeId
	 * @throws MessageBusException
	 */
	public void config(MessageBusClient msgBusClient, String nType, String nodeId) throws MessageBusException {
		this.msgBusClient = msgBusClient;
		this.nodeId = nodeId;
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "PUT", this, "executeTask");
		//TODO: The resource names and method need to be properly named 
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "POST", this, "terminateTask");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/tasks", "DELETE", this, "releaseResource");
		
		msgBusClient.addMethodListener("/" + getNodeId() + "/confirm_node", "PUT", this, "setRegistered");
		
	}
	
	/**
	 * Register to MessageBus.
	 */
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
	 * Starts a stream.	 
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param request is a message bus request
	 * @param flow is a flow that is to be executed
	 */
	public abstract void executeTask(Message request, Stream stream);
	
	/**
	 * Stops sending/receiving data at functional node.
	 * It is supposed send a message to inform its upstream to stop sending data as well.
	 * 
	 * @param flow the flow that is to be terminated
	 */
	public abstract void terminateTask(Flow flow);
	
	/**
	 * Cleans up all resources related to this stream, such as Datagram Socket. It is supposed to send a message to inform its 
	 * downstream to clean up resources as well.
	 * 
	 * @param flow the flow that is to be released
	 */
	public abstract void releaseResource(Flow flow);
	
	/**
	 * Kills all threads that run NodeRunnable, clean up resources such as MethodListners as well.
	 */
	public abstract void reset();

	/**
	 * Gets the a DatagramSocket to receive the stream.
	 * This function is used by Nodes which need to receive data.
	 * @param streamId id of the stream to retrieve the DatagramSocket
	 * @return the DatagramSocket that the stream is binded with
	 */
	public DatagramSocket getAvailableSocket(String streamId) {
		
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
		return udpSocket;
	}
	
	/**
	 * Gets the flow id from a Message.
	 * The from property of message should contain flow id at the end.
	 * @param request the request to fetch the flow id from
	 * @return flow id
	 */
	protected String getFlowId(Message request) {
		String flowId = request.getFrom().toString();
		flowId = flowId.substring(flowId.lastIndexOf('/')+1);
		logger.debug("[RELAY] Flow Id: " + flowId);
		return flowId;
	}	
}
