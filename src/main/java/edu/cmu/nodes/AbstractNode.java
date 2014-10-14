package edu.cmu.nodes;

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

import edu.cmu.messagebus.MessageBusClient;
import edu.cmu.messagebus.exception.MessageBusException;
import edu.cmu.messagebus.test.WorkSpecification;

public abstract class AbstractNode {
	
	protected MessageBusClient msgBusClient;
	
	private String nodeName;

	private NodeType nodeType;
	
	private InetAddress laddr;
	
	/* 1 kb */
	public static final int STD_DATAGRAM_SIZE = 1000;
	
	
	/* 
	 * Note: This may not be sufficient for proper DNS resolution
	 * Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
	 *
	 */
	public AbstractNode() throws UnknownHostException, MessageBusException {
		this("MessageBusClientWarpImpl", NodeType.UNDEF);
	}

	public AbstractNode(NodeType nodeType) throws UnknownHostException, MessageBusException {
		this("MessageBusClientWarpImpl", nodeType);
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
	
	public abstract void connect() throws MessageBusException;
	
	public InetAddress getHostAddr() {
		return laddr;
	}

	public NodeType getNodeType() {
		return nodeType;
	}
	
	public String getNodeName() {
		return nodeName;
	}
	
	
	public abstract void exectueTask(WorkSpecification ws);
	
	private MessageBusClient instantiateMsgBusClient(String className) throws MessageBusException {
		
		MessageBusClient client = null;
		
		Class<?>[] scan;
		try {
			scan = PackageScanner.scan("edu.cmu.messagebus");
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
