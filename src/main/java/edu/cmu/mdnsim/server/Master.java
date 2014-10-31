package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.PackageScanner;
import com.ericsson.research.warp.api.WarpException;
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
import edu.cmu.mdnsim.messagebus.message.StopSimulationRequest;
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
    
    /**
     * Contains a mapping of the node container label to the URI
     */
	private Map<String, String> nodeContainerTbl = new ConcurrentHashMap<String, String>();
	
	/**
	 * Maintains the double direction HashMap. So that the map relationship between
	 * node name and URI can be easily got.
	 */
	private Map<String, String> nodeNameToURITbl =  new ConcurrentHashMap<String, String>();
	private Map<String, String> nodeURIToNameTbl =  new ConcurrentHashMap<String, String>();
	
	/* Used to keep track of the statistics for a stream */
	/**
	 * Key: SimuID, Value: WorkConfig
	 */
	private Map<String, StreamSpec> streamMap = new ConcurrentHashMap<String, StreamSpec>();
	private Map<String, StreamSpec> runningStreamMap = new ConcurrentHashMap<String, StreamSpec>();

	private Map<String, String> startTimeMap = new ConcurrentHashMap<String, String>();
	
	/**
	 * _webClientURI records the URI of the web client
	 */
	private String webClientURI;
	
	/**
	 * Global object representing the nodes and edges as shown in WebClient. 
	 * TODO: This will be initialized or re-initialized whenever users uploads a new simulation script.
	 * And modified whenever any nodes report something.
	 */
	private WebClientGraph webClientGraph = WebClientGraph.INSTANCE;
	
    
    public Master() throws MessageBusException {
    	this("edu.cmu.mdnsim.messagebus.MessageBusServerWarpImpl");
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

			if (msgClass.getName().equals(className)) {
				objectiveMsgBusClass = msgClass;
				break;
			}
		}

		if (objectiveMsgBusClass == null) {
			Exception e = new ClassNotFoundException("Message bus implementation " 
						+ className + " cannot be found");
			throw new MessageBusException(e);
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

		//TODO:Compare registerNode & createNode
		
//		/* Create a new node in the NodeContainer. This is called by WorkSpecification Parser.*/
//		msgBusSvr.addMethodListener("/nodes", "POST", this, "createNode");

		/* Register a new node. This is called from a real Node */
		msgBusSvr.addMethodListener("/nodes", "PUT", this, "registerNode");
		
		/* Register a new node container. This is called from a node container */
		msgBusSvr.addMethodListener("/node_containers", "PUT", this, "registerNodeContainer");
		
		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/validate_user_spec", "POST", this, "validateUserSpec");
		
		/* Once the hosted resource for the front end connects to the domain, it registers itself to the master*/
		msgBusSvr.addMethodListener("/register_webclient", "POST", this, "registerWebClient");

		/* Add listener for web browser call (start simulation) */
		msgBusSvr.addMethodListener("/start_simulation", "POST", this, "startSimulation");

		/* Source report listener */
		msgBusSvr.addMethodListener("/source_report", "POST", this, "sourceReport");

		/* Sink report listener */
		msgBusSvr.addMethodListener("/sink_report", "POST", this, "sinkReport");
		
		/* Add listener for suspend a simulation */
		msgBusSvr.addMethodListener("/simulations", "POST", this, "stopSimulation");
		
		msgBusSvr.register();

    }
    
    /**
     * Master sends node create requests to NodeContainer based on the user WorkSpecification
     * @param req
     */
    private void createNodeOnNodeContainer(CreateNodeRequest req) {
    	
    	//TODO: Handle scenario when there is no node container available for the given label
    	
    	//TODO: Why do we use : to delimit NcLabel
    	System.out.println("[DELETE] Master.createNodeOnNodeContainer(): Node Container label: " + req.getNcLabel());
    	String containerLabel = req.getNcLabel();
    	String ncURI = nodeContainerTbl.get(containerLabel);

    	if (ClusterConfig.DEBUG) {
    		System.out.println("[DEBUG]Master.createNode(): To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " at " + ncURI);
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
	 * The method (listener) is for accepting registration of new real node. 
	 * During the bootstrap of MDNNodes, they connect to Master to register 
	 * itself in the cluster
	 * 
	 * @param request The request message received by message bus
	 * @param registMsg The NodeRegistrationRequest which is encapsulated in 
	 * request
	 */    
	public void registerNode(Message request, RegisterNodeRequest registMsg) {
		
		String nodeName = registMsg.getNodeName();
		nodeNameToURITbl.put(nodeName, registMsg.getURI());
		nodeURIToNameTbl.put(registMsg.getURI(), nodeName);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new "
					+ "node:" + nodeName + " from " + registMsg.getURI());
		}
		try {
			msgBusSvr.send("/nodes", registMsg.getURI()+"/confirm_node", "PUT", registMsg);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * The method (listener) is for accepting registration of new node container.
	 * 
	 * @param msg
	 * @param req
	 */
	public void registerNodeContainer(Message msg, RegisterNodeContainerRequest req) {
		if (nodeContainerTbl.containsKey(req.getLabel())) {
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] MDNManager.registerNodeContainer(): "
						+ "NodeContainer with label " + req.getLabel() 
						+ " already exists");
			}
		} else {
			nodeContainerTbl.put(req.getLabel(), req.getNcURI());
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] MDNManager.registerNodeContainer(): "
						+ "Register new node container label:" 
						+ req.getLabel() + " from " + req.getNcURI());
			}
		}
	}

	/**
	 * Method handler to handle validate user spec message.
	 * Validates user spec and creates the graph
	 * 
	 * @param msg
	 * @param streamSpec
	 */
	public void validateUserSpec(Message mesg, WorkConfig wc) {
		
		HashSet<String> srcSet = new HashSet<String>();
		HashSet<String> sinkSet = new HashSet<String>();
		Map<NodeType, Set<String>> nodesToInstantiate = new HashMap<NodeType, Set<String>>();

		double x = 0.1;
		double y = 0.1;
		String srcRgb = "rgb(0,204,0)";
		String sinkRgb = "rgb(0,204,204)";
		String srcMsg = "This is a Source Node";
		String sinkMsg = "This is a Sink Node";
		String simId = wc.getSimId();
		int nodeSize = 6;
		
		System.out.println("[DELETE]Master.validateUserSpec(): StreamSpec List size = " + wc.getStreamSpecList().size());
		for (StreamSpec streamSpec : wc.getStreamSpecList()) {
			String streamId = streamSpec.StreamId;
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
			
			if (!streamMap.containsKey(streamId))
				streamMap.put(streamId, streamSpec);
		}

		if (webClientURI != null) {
			try {
				msgBusSvr.send("/", webClientURI.toString() + "/create", "POST", webClientGraph.getUpdateMessage());
				System.out.println("Sent update: " + JSON.toJSON(webClientGraph.getUpdateMessage()));
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}

		nodesToInstantiate.put(NodeType.SOURCE, srcSet);
		nodesToInstantiate.put(NodeType.SINK, sinkSet);
		
		if (ClusterConfig.DEBUG) {
			System.out.println("Master.validateUserSpec():  Start to instantiate nodes.");
		}
		instantiateNodes(nodesToInstantiate);
	}
	
	/**
	 * This method (listener) is for accepting registration from web client.
	 * @param msg
	 */
	public void registerWebClient(Message request, String webClientUri) {
		webClientURI = webClientUri;
		System.out.println("Web client URI is "+webClientURI.toString());
	}
	
	/**
	 * Gets a list of sets representing the set of nodes to instantiate in the
	 * deployed NodeContainers
	 * @param nodesToInstantiate
	 */
	private void instantiateNodes(Map<NodeType, Set<String>> nodesToInstantiate) {
		
		for (NodeType nodeType : nodesToInstantiate.keySet()) {
			/* Iterate each nodeType */
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
	 * Triggers the simulation by sending the StreamSpec to the sink
	 * After a StreamSpec is sent to the sink, it is removed from the
	 * "streamMap" hash map and put into the runningStreamMap.
	 * This way, if the user wishes to add new streams to an already running
	 * simulation, this method will send StreamSpec to sink of new streams
	 * 
	 * @param msg
	 * @throws WarpException
	 */

	public void startSimulation(Message msg) throws MessageBusException {
		
		for (StreamSpec streamSpec : streamMap.values()) {
			String sinkUri = updateStreamSpec(streamSpec);
			msgBusSvr.send("/", sinkUri + "/tasks", "PUT", streamSpec);
			runningStreamMap.put(streamSpec.StreamId, streamSpec);
		}
		
		streamMap.clear();
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]Master.startSimulation(): The simulation "
					+ "has started");

		}
	}
	
	/**
	 * 
	 * This method is a helper method that performs 2 functions:
	 * [1] Maps the upstream id to its URI so the node along the chain can send
	 * the data to the destination
	 * [2] Automatically fills in the downstream id and downstream URI based on
	 * the relationship specified by upstream.
	 * 
	 * @param wc WorkConfig waited to be updated
	 * @return the URI of the sink node (starting point of the control message).
	 */
	private String updateStreamSpec(StreamSpec streamSpec) {
		
		String sinkUri = null;
		String sinkNodeId = null;
		
		String downStreamId = null;
		for (int i = 0; i < streamSpec.Flow.size(); i++) {
			
			HashMap<String, String> nodeMap = streamSpec.Flow.get(i);
			nodeMap.put("NodeUri", nodeNameToURITbl.get(nodeMap.get("NodeId")));
			if (i == 0) {
				sinkNodeId = nodeMap.get("NodeId");
				sinkUri = nodeNameToURITbl.get(sinkNodeId);
				downStreamId = nodeMap.get("NodeId");
				nodeMap.put("UpstreamUri", nodeNameToURITbl.get(nodeMap.get("UpstreamId")));
			} else {
				nodeMap.put("DownstreamId", downStreamId);
				nodeMap.put("DownstreamUri", nodeNameToURITbl.get(downStreamId));
				downStreamId = nodeMap.get("NodeId");
				nodeMap.put("UpstreamUri", nodeNameToURITbl.get(nodeMap.get("UpstreamId")));
			}
			
		}
		
		if (ClusterConfig.DEBUG) {
			assert isValidWorkConfig(streamSpec);
		}
		
		return sinkUri;
		
	}
	

	/**
	 * Sends Update message (updated graph) to the web client 
	 * @param webClientUpdateMessage
	 * @throws MessageBusException 
	 */
	private void updateWebClient(WebClientUpdateMessage webClientUpdateMessage)
			throws MessageBusException {
		msgBusSvr.send("/", webClientURI.toString()+  "/update", "POST", webClientUpdateMessage);
	}
	/**
	 * Reports sent by the Source Nodes
	 * @param request
	 * @param srcMsg
	 * @throws MessageBusException 
	 * @throws WarpException
	 */
	public void sourceReport(Message request, SourceReportMessage srcMsg) throws MessageBusException {
		System.out.println("Source started sending data: "+JSON.toJSON(srcMsg));
		String sourceNodeMsg = "Started sending data for stream " + srcMsg.getStreamId() ;
		putStartTime(srcMsg.getStreamId(), srcMsg.getStartTime());
		
		String nodeId = getNodeId(request);
		//TODO: Check if the following synchronization is required (now that we have concurrent hash map in graph)
		Node n = webClientGraph.getNode(nodeId);
		synchronized(n){
			n.tag = sourceNodeMsg;
		}
		if (webClientURI != null) {
			updateWebClient(webClientGraph.getUpdateMessage());
		}

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
	 * @throws MessageBusException 
	 */
	public void sinkReport(Message request, SinkReportMessage sinkMsg) throws MessageBusException {
		
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
		if (webClientURI != null) {
			updateWebClient(webClientGraph.getUpdateMessage());
		}
	}
	
	public void putStartTime(String streamId, String startTime) {
		this.startTimeMap.put(streamId, startTime);
	}
	public String getStartTimeForStream(String streamId) {
		return this.startTimeMap.get(streamId);
	}
    
	public void stopSimulation(StopSimulationRequest req) throws MessageBusException {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]Master.stopSimulation(): Received stop "
					+ "simulation request.");
		}
		
		StreamSpec streamSpec = runningStreamMap.get(req.getStreamID());
		
		msgBusSvr.send("/", streamSpec.findSinkNodeURI() + "/tasks", "POST", streamSpec);
		
		
	
	}
	
    public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException, MessageBusException {
    	Master mdnDomain = new Master();
    	mdnDomain.init();
    }
    
    public static boolean isValidWorkConfig(StreamSpec streamSpec) {
		
		String downStreamNodeId = null;
		String upStreamIdOfDownStreamNode = null;
		for (int i = 0; i < streamSpec.Flow.size(); i++) {
			Map<String, String> nodeMap = streamSpec.Flow.get(i);
			if (i == 0) {
				if (nodeMap.get("DownstreamId") != null) {
					return false;
				}
				downStreamNodeId = nodeMap.get("NodeId");
				upStreamIdOfDownStreamNode = nodeMap.get("UpstreamId");
			} else if (i == streamSpec.Flow.size() - 1){
				if (nodeMap.get("UpstreamId") != null) {
					return false;
				}
				if (!nodeMap.get("DownstreamId").equals(downStreamNodeId)) {
					return false;
				}
			} else {
				if (!nodeMap.get("NodeId").equals(upStreamIdOfDownStreamNode)) {
					return false;
				}
				if (!nodeMap.get("DownstreamId").equals(downStreamNodeId)) {
					return false;
				}
				upStreamIdOfDownStreamNode = nodeMap.get("UpstreamId");
				downStreamNodeId = nodeMap.get("NodeId");
			}
		}

		return true;
	}
}
