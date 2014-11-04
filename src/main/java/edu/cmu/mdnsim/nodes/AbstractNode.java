package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.StopSimulationRequest;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	protected String nodeName;
	
	private InetAddress hostAddr;
	
	private boolean registered = false;
	
	/* 1 kb per datagram */
	public static final int STD_DATAGRAM_SIZE = 1000;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
	/**
	 * Used for reporting purposes. 
	 * Key = Stream Id, Value = UpStreamNodeId
	 */
	protected Map<String,String> upStreamNodes = new HashMap<String,String>();
	/**
	 * Used for reporting purposes.
	 * Key = Stream Id, Value = DownStreamNodeId
	 */
	protected Map<String,String> downStreamNodes = new HashMap<String,String>();
	
	public AbstractNode() throws UnknownHostException {
		/* 
		 * Note: This may not be sufficient for proper DNS resolution
		 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		 */
		hostAddr = java.net.InetAddress.getLocalHost();
	}
	
	public void config(MessageBusClient msgBusClient, NodeType nType, String nName) throws MessageBusException {
		this.msgBusClient = msgBusClient;
		nodeName = nName;
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "PUT", 
				this, "executeTask");
		//TODO: The resource names and method need to be properly named 
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "POST", 
				this, "terminateTask");
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/tasks", "DELETE",
				this, "releaseResource");
		
		msgBusClient.addMethodListener("/" + getNodeName() + "/confirm_node", 
				"PUT", this, "setRegistered");
		
	}
	
	public void register() {
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setNodeName(getNodeName());
		req.setURI(msgBusClient.getURI()+"/"+getNodeName());
		try {
			msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public InetAddress getHostAddr() {
		return hostAddr;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public void setNodeName(String name) {
		this.nodeName = name;
	}	

	public String currentTime(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", 
				Locale.US);
		Date date = new Date();
		return dateFormat.format(date);
	}

	public synchronized void setRegistered(Message msg) {
		registered = true;
		if (ClusterConfig.DEBUG) {
			System.out.println("AbstractNode.setRegistered(): " + getNodeName() 
					+ " successfully registered");
		}
	}
	
	public synchronized boolean isRegistered() {
		return registered;
	}

	/**
	 * 
	 * This methods is to start a stream.
	 * It is supposed to receive/send data and also inform its upstream to 
	 * start appropriate behaviors.
	 * 
	 * @param s
	 */
	public abstract void executeTask(StreamSpec s);
	
	/**
	 * 
	 * This method is to stop sending/receiving data at functional node.
	 * It is supposed send a message to inform its upstream to stop
	 * sending data as well.
	 * 
	 * @param streamSpec
	 */
	public abstract void terminateTask(StreamSpec streamSpec);
	
	/**
	 * 
	 * This method is to clean up all resources related to this stream, such 
	 * as Datagram socket. It is supposed to send a message to inform its 
	 * downstream to clean up resources as well.
	 * 
	 * @param streamSpec
	 */
	public abstract void releaseResource(StreamSpec streamSpec);

}
