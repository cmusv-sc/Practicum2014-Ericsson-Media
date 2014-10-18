package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	protected String nodeName;

	protected NodeType nodeType;
	
	private InetAddress laddr;
	
	private boolean registered = false;
	
	/* 1 kb */
	public static final int STD_DATAGRAM_SIZE = 1000;
	
	public AbstractNode() throws UnknownHostException {
		/* 
		 * Note: This may not be sufficient for proper DNS resolution
		 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		 */
		laddr = java.net.InetAddress.getLocalHost();
	}
	
	public abstract void config() throws MessageBusException;
	public abstract void config(MessageBusClient msgBus, NodeType nType, String nName) throws MessageBusException;
	
	public void register() {
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setType(nodeType);
		req.setNodeName(getNodeName());
		req.setURI(msgBusClient.getURI()+"/"+getNodeName());
		try {
			msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public InetAddress getHostAddr() {
		return laddr;
	}

	public NodeType getNodeType() {
		return nodeType;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	public void setNodeName(String name) {
		this.nodeName = name;
	}
	
	
	public abstract void executeTask(WorkSpecification ws);
	

	public String currentTime(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		Date date = new Date();
		return dateFormat.format(date);
	}

	private synchronized void setRegistered() {
		registered = true;
	}
	
	private synchronized boolean isRegistered() {
		return registered;
	}

}
