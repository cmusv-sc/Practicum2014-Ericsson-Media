package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.trap.utils.PackageScanner;

import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;

public class NodeContainer {

	private MessageBusClient msgBusClient;
	
	private Map<String, AbstractNode> nodeMap;
	
	private String label;
	
	public NodeContainer() throws MessageBusException {
		this("edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl");
	}
	
	public NodeContainer(String messageBusImpl) throws MessageBusException {
		this(messageBusImpl, "default");
	}
	
	public NodeContainer(String messageBusImpl, String label) throws MessageBusException {
		
		msgBusClient= instantiateMsgBusClient(messageBusImpl);
		nodeMap = new ConcurrentHashMap<String, AbstractNode>();
		NodeContainer.this.label = label;
		
	}
	
	public void config() throws MessageBusException {
		msgBusClient.config();
		msgBusClient.addMethodListener("/create_node", "PUT", this, "createNode");
	}
	
	public void connect() throws MessageBusException {
		
		msgBusClient.connect();
		
		RegisterNodeContainerRequest req = new RegisterNodeContainerRequest();
		
		req.setLabel(label);
		req.setNcURI(msgBusClient.getURI());

		msgBusClient.sendToMaster("/", "/node_containers", "PUT", req);
	}
	
	public void createNode(CreateNodeRequest req) 
			throws SecurityException, NoSuchMethodException, 
			IllegalArgumentException, InstantiationException, 
			IllegalAccessException, InvocationTargetException, 
			ClassNotFoundException {
		
		//System.out.println("Create a NODE!!!!! class:" + req.getNodeClass());
		
		Class<?>[] scan = null;
		try {
			scan = PackageScanner.scan("edu.cmu.mdnsim.nodes");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Class<?> objectiveNodeClass = null;
		for (Class<?> nodeClass : scan) {
			if (nodeClass.getName().equals(req.getNodeClass())) {
				objectiveNodeClass = nodeClass;
				break;
			}
		}
		
		if (objectiveNodeClass == null) {
			throw new ClassNotFoundException("Class (" + req.getNodeClass() + ") cannot"
					+ " be found.");
		}
		
		Constructor<?> constructor = objectiveNodeClass.getConstructor();
		AbstractNode newNode = (AbstractNode)constructor.newInstance();
		try {
			newNode.config(msgBusClient, req.getNodeType(), req.getNcLabel());
			newNode.register();
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		nodeMap.put(newNode.getNodeName(), newNode);
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] NodeContainer.createNode(): Instantiate"
					+ " a new node " + objectiveNodeClass.getCanonicalName());
		}
		
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
			//System.out.println(msgClass.getCanonicalName());
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
	
	public static void main(String[] args) throws MessageBusException {
		
		NodeContainer nc = null;
		
		int beginIndex = 6; // the node label will start after the prefix "label:" i.e. the 7th char
		
		if (args != null && args.length > 0 && 
				args[0] != null && args[0].startsWith("label:")) {
			nc = new NodeContainer("edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl", 
					args[0].substring(beginIndex));
		}
		else {
			nc = new NodeContainer();
		}
		nc.config();
		nc.connect();
	}

}
