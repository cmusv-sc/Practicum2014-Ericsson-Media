package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.PackageScanner;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusServer;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
import edu.cmu.mdnsim.nodes.NodeType;
import edu.cmu.mdnsim.server.WebClientGraph.Edge;
import edu.cmu.mdnsim.server.WebClientGraph.Node;
import edu.cmu.mdnsim.server.WebClientGraph.NodeLocation;
/**
 * It represents the Master Node of the Simulator.
 * Some of the major responsibilities include: 
 * 1. Communicating with WebClient - Parsing user input (work specification) and sending regular updates
 * 2. Keeping track of all active nodes in the system
 * 3. Keeping track of all Node Containers
 * 4. Naming of the nodes and mapping them to the names given by the user
 * 5. Staring Message Bus Server and registering resource for reporting
 * 
 * @author CMU-SV Ericsson Media Team
 *
 */
public class Master {
    /**
     * The Message Bus Server is part of the master node and is started 
     */
    MessageBusServer msgBusSvr;
    
//    /**
//	 * The _nodeTbl is a table that maps the name of MDNNode to WarpURI of 
//	 * MDNNode
//	 */
//	private ConcurrentHashMap<String, RegisterNodeRequest> _nodeTbl = 
//			new ConcurrentHashMap<String, RegisterNodeRequest>();
//	
//	/**
//	 * The labelTbl is the table that maps the label to the node name.
//	 */
//	private ConcurrentHashMap<String, RegisterNodeContainerRequest> _labelTbl = 
//			new ConcurrentHashMap<String, RegisterNodeContainerRequest>();
    
    /**
     * Contains a mapping of the node container label to the URI
     */
	private HashMap<String, String> nodeContainerTbl = new HashMap<String, String>();
	
	private HashMap<String, String> _nodeTbl =  new HashMap<String, String>();
	private HashMap<String, String> nodeURIToName =  new HashMap<String, String>();
	
	/**
	 * _webClientURI records the URI of the web client
	 */
	private String _webClientURI;
	/**
	 * Global object representing the nodes and edges as shown in WebClient. 
	 * TODO: This will be initialized or re-initialized whenever users uploads a new simulation script.
	 * And modified whenever any nodes report something.
	 */
	private WebClientGraph webClientGraph = WebClientGraph.INSTANCE;
	
	private HashMap<String, WorkConfig> simulationMap;

	private HashMap<String, String> _startTimeMap;
    
    public Master() throws MessageBusException {
    	msgBusSvr = instantiateMsgBusServer("edu.cmu.mdnsim.messagebus.MessageBusServerWarpImpl");
    	_startTimeMap = new HashMap<String, String>();
    }
    
    public Master(String msgBusSvrClassName) throws MessageBusException {
    	msgBusSvr = instantiateMsgBusServer(msgBusSvrClassName);
    }
    
    /**
     * Takes in the MessageBus class name used for communicating among the nodes and 
     * initializes the MessageBusServer interface with object of that class
     * To add a new MessageBus implementation, simply add a class to the "edu.cmu.messagebus" 
     * package and pass that class name to this method
     * Currently the following MessageBus implementations are supported
     * 1) Warp [Ericsson's system][ClassName: "MessageBusServerWarpImpl"]
     * 
     * @param className
     * @return
     * @throws MessageBusException
     */
    private MessageBusServer instantiateMsgBusServer(String className) throws MessageBusException {
    	MessageBusServer server = null;
    	
    	Class<?>[] scan;
		try {
			scan = PackageScanner.scan("edu.cmu.mdnsim.messagebus");
		} catch (IOException e) {
			throw new MessageBusException(e);
		}
		
		Class<?> objectiveMsgBusClass = null;
		for (Class<?> msgClass : scan) {
//			System.out.println(msgClass.getName() + " ?= " + className);
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
			server = (MessageBusServer) defaultConstructor.newInstance();
		} catch (IllegalArgumentException e) {
			throw new MessageBusException(e);
		} catch (InstantiationException e) {
			throw new MessageBusException(e);
		} catch (IllegalAccessException e) {
			throw new MessageBusException(e);
		} catch (InvocationTargetException e) {
			throw new MessageBusException(e);
		}

		return server;
	}

	/**
     * 
     * Initialize the message bus server
     * 
	 * Initialization of MdnMaster. Specifically, it registers itself with 
	 * Warp domain and obtain the WarpURI. Warp provides straightforward WarpURI
	 * for service("warp://provider_name:service_name"); It also registers some
	 * method listener to handle requests from Web interface and MDNNode in the
	 * control message layer
	 * 
	 * @throws WarpException. IOException and TrapException
	 * @throws MessageBusException 
	 */
    public void init() throws WarpException, IOException, TrapException, MessageBusException {

		msgBusSvr.config();

		/* Used to keep track of the statistics for a stream */
		simulationMap = new HashMap<String, WorkConfig>();

//		/* Create a new node in the NodeContainer. This is called by WorkSpecification Parser.*/
//		msgBusSvr.addMethodListener("/nodes", "POST", this, "createNode");

		/* Register a new node. This is called from a real Node */
		msgBusSvr.addMethodListener("/nodes", "PUT", this, "registerNode");
		
		/* Register a new node. This is called from a Node Container */
		msgBusSvr.addMethodListener("/node_containers", "PUT", this, "registerNodeContainer");
		
		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/validate_user_spec", "POST", this, "validateUserSpec");
		
		/* Once the hosted resource for the front end connects to the domain, it registers itself to the master*/
		msgBusSvr.addMethodListener("/register_webclient", "POST", this, "registerWebClient");

		/* Add listener for web browser call (start simulation) */
		msgBusSvr.addMethodListener("/start_simulation", "POST", this,
				"startSimulation");

		/* Source report listener */
		msgBusSvr.addMethodListener("/source_report", "POST", this,
				"sourceReport");

		/* Sink report listener */
		msgBusSvr.addMethodListener("/sink_report", "POST", this, "sinkReport");


		msgBusSvr.register();

    }
    /**
     * Sends node creation message to appropriate node container. 
     * Node container is identified by label in node creation request.
     * @param message The incoming message for creation of node
     * @param req Associated node creation request object
     */
//    public void createNode(Message message, CreateNodeRequest req) {
//    	//TODO: Handle scenario when there is no node container available for the given label
//    	String ncURI = nodeContainerTbl.get(req.getNcLabel());
//    	if (ClusterConfig.DEBUG) {
//    		System.out.println("[DEBUG]Master.createNode(): To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " at " + ncURI);
//    		System.out.println("Class = " + req.getNodeClass());
//    	}
//    	
//    	try {
//    		msgBusSvr.send("/", ncURI + "/create_node", "PUT", req);
//    	} catch (MessageBusException e) {
//    		e.printStackTrace();
//    	}
//    	
//    	if (ClusterConfig.DEBUG) {
//    		System.out.println("[DEBUG]Master.createNode(): message sent");
//    	}
//    }
    
    /**
     * Master sends node create requests to NodeContainer based on the user WorkSpecification
     * @param req
     */
    public void createNodeOnNodeContainer(CreateNodeRequest req) {
    	//TODO: Handle scenario when there is no node container available for the given label
    	String containerLabel = req.getNcLabel().split(":")[0];
    	String ncURI = nodeContainerTbl.get(containerLabel);
    	System.out.println("Sending node create request to node container URI "+ncURI);
    	if (ClusterConfig.DEBUG) {
    		System.out.println("[DEBUG]Master.createNode(): To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " at " + ncURI);
    		System.out.println("Class = " + req.getNodeClass());
    	}
    	
    	try {
    		msgBusSvr.send("/", ncURI + "/create_node", "PUT", req);
    	} catch (MessageBusException e) {
    		e.printStackTrace();
    	}
    	
    	if (ClusterConfig.DEBUG) {
    		System.out.println("[DEBUG]Master.createNode(): message sent");
    	}
    }
	
	/**
	 * The method listener for initialization of MDNNode. During the bootstrap 
	 * of MDNNodes, they connect to MDNManager to register itself in the cluster
	 * 
	 * @param request The request message received by message bus
	 * @param registMsg The NodeRegistrationRequest which is encapsulated in 
	 * request
	 */    
	public void registerNode(Message request, RegisterNodeRequest registMsg) {
		
		String nodeName = registMsg.getNodeName();
		_nodeTbl.put(nodeName, registMsg.getURI());
		this.nodeURIToName.put(registMsg.getURI(), nodeName);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + nodeName + " from " + registMsg.getURI());
		}
		try {
			msgBusSvr.send("/nodes", registMsg.getURI()+"/confirm_node", "PUT", registMsg);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	public void registerNodeContainer(Message msg, RegisterNodeContainerRequest req) {
		if (nodeContainerTbl.containsKey(req.getLabel())) {
			System.out.println("NodeContainer with label "+req.getLabel()+" already exists");
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] MDNManager.registerNodeContainer(): NodeContainer with label "
						+ req.getLabel()+" already exists");
			}
		} else {
			nodeContainerTbl.put(req.getLabel(), req.getNcURI());
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] MDNManager.registerNodeContainer(): Register new node container label:" 
						+ req.getLabel() + " from " + req.getNcURI());
			}
		}
	}

	/**
	 * Method handler to handle validate user spec message.
	 * Validates user spec and creates the graph
	 * @param msg
	 * @param streamSpec
	 */
	public void validateUserSpec(Message mesg, WorkConfig wc) {
		
		HashSet<String> srcSet = new HashSet<String>();
		HashSet<String> sinkSet = new HashSet<String>();
		HashMap<NodeType, HashSet<String>> nodesToInstantiate = new HashMap<NodeType, HashSet<String>>();
		
		double x = 0.1;
		double y = 0.1;
		String srcRgb = "rgb(0,204,0)";
		String sinkRgb = "rgb(0,204,204)";
		String srcMsg = "This is a Source Node";
		String sinkMsg = "This is a Sink Node";
		String simId = wc.getSimId();
		int nodeSize = 6;
				
		for (StreamSpec streamSpec : wc.getStreamSpecList()) {
			System.out.println("Stream Id is "+streamSpec.StreamId);
			System.out.println("DataSize is "+streamSpec.DataSize);
			System.out.println("ByteRate is "+streamSpec.ByteRate);

			for (HashMap<String, String> node : streamSpec.Flow) {
				String nType = node.get("NodeType");
				String nId = node.get("NodeId");
				String upstreamNode = node.get("UpstreamId");
				String rgb = "";
				String msg = "";
				String edgeId = nId+"-"+upstreamNode;
				String edgeType = "";
				
				if (webClientGraph.getNode(nId) == null) {
					/*
					 * The aboce check is used to ensure that the node is added only once to the node list.
					 * This situation can arise if a sink node is connected to two upstream sources at the 
					 * same time
					 */
					//TODO: Come up with some better logic to assign positions to the nodes
					NodeLocation nl = webClientGraph.getNewNodeLocation();
					x = nl.x;
					y = nl.y;
					if (nType.equals("SOURCE")) {
						rgb = srcRgb;
						msg = srcMsg;
						srcSet.add(nId);
					} else if (nType.equals("SINK")) {
						rgb = sinkRgb;
						msg = sinkMsg;
						sinkSet.add(nId);
					}
					Node n = webClientGraph.new Node(nId, nId, x, y, rgb, nodeSize,  msg);					
					webClientGraph.addNode(n);
				}

				if(!upstreamNode.equals("NULL") && webClientGraph.getEdge(edgeId) == null) {
					Edge e = webClientGraph.new Edge(edgeId, upstreamNode, nId, edgeType, edgeId);
					webClientGraph.addEdge(e);
				}
				System.out.println("**** Node in Flow ****");
				System.out.println("Recevied flow is "+nType +" "+ nId +" "+upstreamNode);
			}
		}

		try {
			//TODO: If the graph is already created send update message
			Warp.send("/", WarpURI.create(_webClientURI.toString() + "/create"), "POST", 
					JSON.toJSON(webClientGraph.getUpdateMessage()).getBytes());
			System.out.println("Sent update: " + JSON.toJSON(webClientGraph.getUpdateMessage()));
		} catch (WarpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		nodesToInstantiate.put(NodeType.SOURCE, srcSet);
		nodesToInstantiate.put(NodeType.SINK, sinkSet);
		simulationMap.put(simId, wc);
		
		instantiateNodes(nodesToInstantiate);
	}
	
	/**
	 * Handler for the registration message from the web client
	 * @param msg
	 */
	public void registerWebClient(Message request, String webClientUri) {
		_webClientURI = webClientUri;
		System.out.println("Web client URI is "+_webClientURI.toString());
	}
	
	/**
	 * Gets a list of sets representing the set of nodes to instantiate in the
	 * deployed NodeContainers
	 * @param nodesToInstantiate
	 */
	public void instantiateNodes(HashMap<NodeType, HashSet<String>> nodesToInstantiate) {
		for (NodeType nodeType : nodesToInstantiate.keySet()) {
			for (String node : nodesToInstantiate.get(nodeType)) {
				System.out.println(node);
				String nodeClass = "";
				if (nodeType == NodeType.SOURCE)
					nodeClass = "edu.cmu.mdnsim.nodes.SourceNode";
				else if (nodeType == NodeType.SINK)
					nodeClass = "edu.cmu.mdnsim.nodes.SinkNode";
				CreateNodeRequest req = new CreateNodeRequest(nodeType, node, nodeClass);
				createNodeOnNodeContainer(req);
			}
		}
	}
	
	/**
	 * Triggers the simulation by sending the 
	 * work specification to the sink
	 * @param msg
	 * @throws WarpException
	 */
	public void startSimulation(Message msg) throws WarpException {
		String sinkUri = "";
		for (WorkConfig wc : simulationMap.values()) {
			for (StreamSpec s : wc.getStreamSpecList()) {
				for (HashMap<String, String> f : s.Flow) {
					String upstream = f.get("UpstreamId");
					if (sinkUri.equals(""))
						sinkUri = _nodeTbl.get(f.get("NodeId"));
					if (_nodeTbl.containsKey(upstream))
						f.put("UpstreamUri", _nodeTbl.get(upstream));
				}
			}
			try {
				msgBusSvr.send("/", sinkUri+"/tasks", "PUT", wc);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sends Update message (updated graph) to the web client 
	 * @param webClientUpdateMessage
	 * @throws WarpException
	 */
	private void updateWebClient(WebClientUpdateMessage webClientUpdateMessage)
			throws WarpException {
		Warp.send("/", WarpURI.create(_webClientURI.toString()+"/update"), "POST", 
				JSON.toJSON(webClientUpdateMessage).getBytes() );
	}
	/**
	 * Reports sent by the Source Nodes
	 * @param request
	 * @param srcMsg
	 * @throws WarpException
	 */
	public void sourceReport(Message request, SourceReportMessage srcMsg) throws WarpException {
		System.out.println("Source started sending data: "+JSON.toJSON(srcMsg));
		//Warp.send("/", WarpURI.create(_webClientURI.toString()+"/update"), "POST", "simulationStarted".getBytes(),"text/plain" );
		String sourceNodeMsg = "Started sending data for stream " + srcMsg.getStreamId() ;
		putStartTime(srcMsg.getStreamId(), srcMsg.getStartTime());
		
		String nodeId = getNodeId(request);
		//TODO: Check if the following synchronization is required (now that we have concurrent hash map in graph)
		Node n = webClientGraph.getNode(nodeId);
		synchronized(n){
			n.tag = sourceNodeMsg;
		}
		updateWebClient(webClientGraph.getUpdateMessage());
	}
	/**
	 * Extracts node id from messageRequest.from
	 * It will throw runtime exceptions if message is not in proper format
	 * @param request
	 * @return NodeId
	 */
	private String getNodeId(Message request) {
		String nodeId = request.getFrom().toString();
		nodeId = nodeId.substring(0,nodeId.lastIndexOf('/'));
		nodeId = nodeId.substring(nodeId.lastIndexOf('/')+1);
		return nodeId;
	}
	/**
	 * The report sent by the sink nodes
	 * @param request
	 * @param sinkMsg
	 * @throws WarpException
	 */
	public void sinkReport(Message request, SinkReportMessage sinkMsg) throws WarpException {
		
		long totalTime = 0;
		System.out.println("Sink finished receiving data: "+JSON.toJSON(sinkMsg));
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		try {
			totalTime = df.parse(sinkMsg.getEndTime()).getTime() - 
					df.parse(getStartTimeForStream(sinkMsg.getStreamId())).getTime();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			totalTime = -1;
		}
				
		String sinkNodeMsg = "Done receiving data for stream " + sinkMsg.getStreamId() + " . Got " + 
				sinkMsg.getTotalBytes() + " bytes. Time Taken: " + totalTime + " ms." ;
		
		String nodeId = getNodeId(request);
		
		Node n = webClientGraph.getNode(nodeId);
		//TODO: Check if the following synchronization is required (now that we have concurrent hash map in graph)
		synchronized(n){
			n.tag = sinkNodeMsg;
		}
		updateWebClient(webClientGraph.getUpdateMessage());
	}
	
	public void putStartTime(String streamId, String startTime) {
		this._startTimeMap.put(streamId, startTime);
	}
	public String getStartTimeForStream(String streamId) {
		return this._startTimeMap.get(streamId);
	}

    
    public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException, MessageBusException {
    	Master mdnDomain = new Master();
    	mdnDomain.init();
    }
}
