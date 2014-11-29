package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.trap.utils.PackageScanner;
import com.ericsson.research.warp.api.rest.DELETE;
import com.ericsson.research.warp.api.rest.PUT;
import com.ericsson.research.warp.api.rest.Path;
import com.ericsson.research.warp.api.rest.PathParam;
import com.ericsson.research.warp.api.rest.WarpRestConverter;

import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;

public class NodeContainer {
		
	public static ExecutorService ThreadPool = Executors.newCachedThreadPool();
	
	public static final String NODE_COLLECTION_PATH = "/nodes";
	
	private MessageBusClient msgBusClient;
	/**
	 * Key = Node Id, Value = Node
	 * This map stores all the nodes hosted by node container
	 */
	private Map<String, AbstractNode> nodeMap;
	/**
	 * Label uniquely identifies a Node Container in entire simulation
	 */
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
	/**
	 * Configure the message bus.
	 * @throws MessageBusException
	 */
	public void config() throws MessageBusException {
		msgBusClient.config();

		WarpRestConverter.convert(this, false);
		
		//msgBusClient.addMethodListener(NODE_COLLECTION_PATH + "/{nodeId}", "PUT", this, "createNode");
		
		//msgBusClient.addMethodListener(NODE_COLLECTION_PATH, "DELETE", this, "cleanUpNodes");
	}
	/**
	 * Connects the Node Container to Master node
	 * And sends a registration request to master which will help master node know URI of node container
	 * @throws MessageBusException
	 */
	public void connect() throws MessageBusException {
		
		msgBusClient.connect();
		
		RegisterNodeContainerRequest req = new RegisterNodeContainerRequest();
		
		req.setLabel(label);
		req.setNcURI(msgBusClient.getURI());
		
		msgBusClient.sendToMaster("/", "/node_containers", "PUT", req);
	
	}

	@Path(NODE_COLLECTION_PATH + "/{nodeId}")
	@PUT
	public void createNode(@PathParam("nodeId") String nodeId, CreateNodeRequest req) 
			throws SecurityException, NoSuchMethodException, 
			IllegalArgumentException, InstantiationException, 
			IllegalAccessException, InvocationTargetException, 
			ClassNotFoundException {
		
		
		if (nodeMap.containsKey(req.getNodeId())) {
			return;
		}
		
		Class<?>[] scan = null;
		try {
			scan = PackageScanner.scan("edu.cmu.mdnsim.nodes");
		} catch (IOException e) {
			e.printStackTrace();
		} if (scan == null) {
			throw new ClassNotFoundException("No class is found in this folder");
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
			newNode.config(msgBusClient, req.getNodeType(), req.getNodeId());
			newNode.register();
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		nodeMap.put(newNode.getNodeId(), newNode);
		
		if (ClusterConfig.DEBUG) {
			System.out.format("[DEBUG] NodeContainer.createNode(): Instantiate"
					+ " a new node (param=%s; newNodeInfo=%s)\n", nodeId, newNode.getNodeId());
		}
		
	}
	
	@Path(NODE_COLLECTION_PATH)
	@DELETE
	public void reset() {
		System.out.println("[DEBUG]NodeContainer.reset(): Total node number = " + nodeMap.size());
		for (String nodeId : nodeMap.keySet()) {
			System.out.println("[DEBUG]NodeContainer.reset(): Clean the " + nodeId);
			stopNode(nodeId);
		}
	}
	
	private void stopNode(String nodeId) {
		AbstractNode node = nodeMap.get(nodeId);
		node.reset();
		nodeMap.remove(nodeId);
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
	
	public static void main(String[] args) throws MessageBusException {
		
		NodeContainer nc = null;
		
		/* the node label will start after the prefix "label:" i.e. the 7th char */
		int beginIndex = 6; 
		
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
