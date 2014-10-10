package edu.cmu.nodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.ericsson.research.trap.utils.PackageScanner;

import edu.cmu.global.ClusterConfig;
import edu.cmu.messagebus.MessageBusClient;
import edu.cmu.messagebus.MessageBusClientWarpImpl;
import edu.cmu.messagebus.exception.MessageBusException;



public class NodeContainer {

	private MessageBusClient msgBusClient;
	
	private List<AbstractNode> nodeList;
	
	private String label;
	
	public NodeContainer() throws MessageBusException {
		msgBusClient = new MessageBusClientWarpImpl();
		nodeList = new ArrayList<AbstractNode>();
		label = "default";
	}
	
	public NodeContainer(String messageBusImpl) throws MessageBusException {
		
		msgBusClient = instantiateMsgBusClient(messageBusImpl);
		nodeList = new ArrayList<AbstractNode>();
		NodeContainer.this.label = "default";
	}
	
	public NodeContainer(String messageBusImpl, String label) throws MessageBusException {
		
		msgBusClient= instantiateMsgBusClient(messageBusImpl);
		nodeList = new ArrayList<AbstractNode>();
		NodeContainer.this.label = label;
		
	}
	
	public void config() throws MessageBusException {
		msgBusClient.config();
		msgBusClient.addMethodListener("/create-node", "PUT", this, "createNode");
		msgBusClient.connect();
	}
	
	public void createNode(String nodeType) 
			throws SecurityException, NoSuchMethodException, 
			IllegalArgumentException, InstantiationException, 
			IllegalAccessException, InvocationTargetException, 
			ClassNotFoundException {
		
		Class<?>[] scan = null;
		try {
			scan = PackageScanner.scan("edu.cmu.Nodes");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Class<?> objectiveNodeClass = null;
		for (Class<?> nodeClass : scan) {
			if (nodeClass.getName().equals(nodeType)) {
				objectiveNodeClass = nodeClass;
				break;
			}
		}
		
		if (objectiveNodeClass == null) {
			throw new ClassNotFoundException("Class (" + nodeType + ") cannot"
					+ " be found.");
		}
		
		Constructor<?> constructor = objectiveNodeClass.getConstructor();
		AbstractNode newNode = (AbstractNode)constructor.newInstance();
		
		nodeList.add(newNode);
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] NodeContainer.createNode(): Instantiate"
					+ " a new node " + objectiveNodeClass.getCanonicalName());
		}
		
	}
	
	
	private MessageBusClient instantiateMsgBusClient (String className) throws MessageBusException {
		
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
