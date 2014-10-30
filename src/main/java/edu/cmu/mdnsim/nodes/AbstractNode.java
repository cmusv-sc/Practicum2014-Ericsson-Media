package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.ericsson.research.warp.api.message.Message;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	protected String nodeName;
	
	private InetAddress hostAddr;
	
	private boolean registered = false;
	
	/* 1 kb per datagram */
	public static final int STD_DATAGRAM_SIZE = 1000;
	
	public static final int MILLISECONDS_PER_SECOND = 1000;
	
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

	public abstract void executeTask(StreamSpec s);

}
