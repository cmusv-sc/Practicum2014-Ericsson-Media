package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.ericsson.research.trap.utils.PackageScanner;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	private String nodeName;

	protected NodeType nodeType;
	
	private InetAddress laddr;
	
	private boolean registered = false;
	
	/* 1 kb */
	public static final int STD_DATAGRAM_SIZE = 1000;
	
	
	/* 
	 * Note: This may not be sufficient for proper DNS resolution
	 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
	 *
	 */
	public AbstractNode() throws UnknownHostException, MessageBusException {
		this("edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl", NodeType.UNDEF);
	}

	public AbstractNode(NodeType nodeType) throws UnknownHostException, MessageBusException {
		this("edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl", nodeType);
	}
	
	public AbstractNode(String msgBusClientImplName) throws UnknownHostException, MessageBusException {
		this(msgBusClientImplName, NodeType.UNDEF);
	}
	
	public AbstractNode(String msgBusClientImplName, NodeType nodeType) throws UnknownHostException, MessageBusException {
		msgBusClient = instantiateMsgBusClient(msgBusClientImplName);
		AbstractNode.this.nodeType = nodeType;
		laddr = java.net.InetAddress.getLocalHost();
	}
	
	public abstract void config() throws MessageBusException;	
	
	public void connect() throws MessageBusException {
		msgBusClient.addMethodListener("/nodename", "POST", this, "registerNodeReply");
		msgBusClient.connect();
		RegisterNodeRequest req = new RegisterNodeRequest();
		req.setType(nodeType);
		while (!msgBusClient.isConnected()) {
			
		}
		msgBusClient.sendToMaster("/", "/nodes", "PUT", req);
		while (!isRegistered()) {
			
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
	
	private MessageBusClient instantiateMsgBusClient (String className) throws MessageBusException {
		
		MessageBusClient client = null;
		
		Class<?>[] scan;
		try {
			scan = PackageScanner.scan("edu.cmu.mdnsim.messagebus");
		} catch (IOException e) {
			throw new MessageBusException(e);
		}
		
		Class<?> objectiveMsgBusClass = null;
		for (Class<?> msgClass : scan) {
			if (msgClass.getName().equals(className)) {
				objectiveMsgBusClass = msgClass;
				break;
			}
		}
		if (objectiveMsgBusClass == null) {
			try {
				throw new ClassNotFoundException("Message bus implementation " 
						+ className + " cannot be found");
			} catch (ClassNotFoundException e) {
				throw new MessageBusException(e);
			}
		}
		
		Constructor<?> defaultConstructor;
		try {
			defaultConstructor = objectiveMsgBusClass.getConstructor();
		} catch (SecurityException e) {
			throw new MessageBusException(e);
		} catch (NoSuchMethodException e) {
			throw new MessageBusException(e);
		}
		
		
		try {
			client = (MessageBusClient) defaultConstructor.newInstance();
		} catch (IllegalArgumentException e) {
			throw new MessageBusException(e);
		} catch (InstantiationException e) {
			throw new MessageBusException(e);
		} catch (IllegalAccessException e) {
			throw new MessageBusException(e);
		} catch (InvocationTargetException e) {
			throw new MessageBusException(e);
		}
		
		return client;
	}


}
