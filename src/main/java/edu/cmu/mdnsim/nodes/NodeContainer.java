package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.yamb.rmb.annotations.DELETE;
import us.yamb.rmb.annotations.PUT;
import us.yamb.rmb.annotations.Path;
import us.yamb.rmb.annotations.PathParam;

import com.ericsson.research.trap.utils.PackageScanner;

import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;
import edu.cmu.mdnsim.reporting.SystemClock;
import edu.cmu.mdnsim.server.Master;

/**
 * NodeContainer is an entity that can host multiple nodes. The types of nodes,
 * the number of each type nodes are configured during the runtime. It holds a 
 * {@link MessageBusClient} to allow communication with {@link Master}. Besides,
 * it passes object of {@link MessageBusClient} to {@link AbstractNode}. 
 * {@link MessageBusClient} is one per each JVM.
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class NodeContainer {
		
	public static ExecutorService ThreadPool = Executors.newCachedThreadPool();
	
	private Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.node-container");
	
	
	public static final String NODE_COLLECTION_PATH = "/nodes";
	
	
	private static final String KEY_LABEL = "label";
	
	private static final String KEY_MASTER_IP = "master_ip";
	
	private static final String KEY_NODECONTAINER_IP = "nc_ip";
	
	private static final String DEFAULT_MSGBUS_IMPL = "edu.cmu.mdnsim.messagebus.MessageBusClientRMBImpl";
	
	private MessageBusClient msgBusClient;
	/**
	 * Key = Node Id, Value = Node
	 * This map stores all the nodes hosted by node container
	 */
	private Map<String, AbstractNode> nodeMap;
	/**
	 * Label uniquely identifies a Node Container in entire simulation
	 */
	private final String label;
	
	private String nodeContainerIP;
	
	
	private String masterIP;
	
	
	public NodeContainer(String messageBusImpl, String label, String masterIP, String nodeContainerIP) throws MessageBusException {
		this.masterIP = masterIP;
		msgBusClient= instantiateMsgBusClient(messageBusImpl, masterIP);
		nodeMap = new ConcurrentHashMap<String, AbstractNode>();
		this.label = label;
		this.nodeContainerIP = nodeContainerIP;
		SystemClock.currentTimeMillis();
		
	}
	
	/**
	 * Configure the message bus.
	 * @throws MessageBusException
	 */
	public void config() throws MessageBusException {
		
		msgBusClient.config();
		msgBusClient.addMethodListener(NODE_COLLECTION_PATH + "/{nodeId}", "PUT", this, "createNode");
		msgBusClient.addMethodListener(NODE_COLLECTION_PATH, "DELETE", this, "reset");

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
			logger.debug("NodeContainer.createNode(): To instantiate " + nodeClass.getName());
			if (nodeClass.getName().equals(req.getNodeClass())) {
				objectiveNodeClass = nodeClass;
				break;
			}
		}
		
		if (objectiveNodeClass == null) {
			throw new ClassNotFoundException("Class (" + req.getNodeClass() + ") cannot"
					+ " be found.");
		}
		
		Constructor<?> constructor = objectiveNodeClass.getConstructor(new Class<?>[] {String.class, String.class});
		
		AbstractNode newNode = (AbstractNode)constructor.newInstance(nodeContainerIP, masterIP);
		
		try {
			newNode.config(msgBusClient, req.getNodeType(), req.getNodeId());
			newNode.register();
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		nodeMap.put(newNode.getNodeId(), newNode);
		logger.debug("Instantiate a new node (nodeInfo=" + newNode.getNodeId());
		
		
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
	
	
	private static MessageBusClient instantiateMsgBusClient (String className, String masterIP) throws MessageBusException {
		
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
			defaultConstructor = objectiveMsgBusClass.getConstructor(new Class<?>[]{String.class});
		} catch (SecurityException e) {
			throw new MessageBusException(e);
		} catch (NoSuchMethodException e) {
			throw new MessageBusException(e);
		}
		
		
		try {
			client = (MessageBusClient) defaultConstructor.newInstance(masterIP);
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

		Map<String, String> argsMap = getOpt(args);
		if (argsMap.get(KEY_LABEL) == null) {
			System.out.format("Failed to initiate NodeContainer because of missing label.\n");
			System.exit(1);
		}
		if (argsMap.get(KEY_MASTER_IP) == null) {
			
			System.out.format("Failed to initiate NodeContainer because of missing Master IP.\n");
			System.exit(1);
			
		}
		if (argsMap.get(KEY_NODECONTAINER_IP) == null) {
			try {
				argsMap.put(KEY_NODECONTAINER_IP, InetAddress.getLocalHost().getHostAddress());
			} catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		nc = new NodeContainer(DEFAULT_MSGBUS_IMPL, argsMap.get(KEY_LABEL), argsMap.get(KEY_MASTER_IP), argsMap.get(KEY_NODECONTAINER_IP));
		nc.config();
		nc.connect();
	}
	
	private static Map<String, String>getOpt(String[] args) {
		Map<String, String> ret = new HashMap<String, String>();
		if (args== null) {
			return ret;
		}
		
		for (String arg : args) {
			String key = parseKey(arg);
			if (KEY_LABEL.equals(key)) {
				ret.put(KEY_LABEL, parseValue(arg));
			} else if (KEY_MASTER_IP.equals(key)) {
				ret.put(KEY_MASTER_IP, parseValue(arg));
			} else if (KEY_NODECONTAINER_IP.equals(key)) {
				ret.put(KEY_NODECONTAINER_IP, parseValue(arg));
			}
		}

		return ret;
	}
	
	private static String parseKey(String arg) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < arg.length(); i++) {
			if (arg.charAt(i) == ':') {
				break;
			} else {
				sb.append(arg.charAt(i));
			}
		}
		return sb.toString();
	}
	
	private static String parseValue(String arg) {

		int i = 0;
		for (i = 1; i < arg.length(); i++) {
			if (arg.charAt(i) == ':') {
				i++;
				break;
			}
			
		}
		return arg.substring(i);
	}
	

}
