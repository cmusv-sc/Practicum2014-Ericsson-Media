package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.PackageScanner;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusServer;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
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
import edu.cmu.util.Utility;
/**
 * It represents the Master Node of the Simulator.
 * Some of the major responsibilities include: 
 * 1. Communicating with WebClient - Parsing user input (work specification) and sending regular updates
 * 2. Keeping track of all active nodes in the system
 * 3. Keeping track of all Node Containers
 * 4. Naming of the nodes and mapping them to the names given by the user
 * 5. Staring Message Bus Server and registering resource for reporting
 * 
 * @author Jeremy Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
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
	 * Key: SimID, Value: WorkConfig
	 */
	private Map<String, Stream> streamMap = new ConcurrentHashMap<String, Stream>();
	private Map<String, Stream> runningStreamMap = new ConcurrentHashMap<String, Stream>();
	/**
	 * Map of flow id's to a Flow. The flows in this map have not started yet
	 */
	private Map<String, Flow> flowMap = new ConcurrentHashMap<String, Flow>();
	private Map<String, Flow> runningFlowMap = new ConcurrentHashMap<String, Flow>();

	private Map<String, String> startTimeMap = new ConcurrentHashMap<String, String>();

	/**
	 * webClientURI records the URI of the web client
	 */
	private String webClientURI;

	/**
	 * Global object representing the nodes and edges as shown in WebClient. 
	 * This will be initialized or updated whenever users uploads a new simulation script.
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

		/* Register a new node. This is called from a real Node */
		msgBusSvr.addMethodListener("/nodes", "PUT", this, "registerNode");

		/* Register a new node container. This is called from a node container */
		msgBusSvr.addMethodListener("/node_containers", "PUT", this, "registerNodeContainer");

		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/work_config", "POST", this, "uploadWorkConfig");

		/* Once the hosted resource for the front end connects to the domain, it registers itself to the master*/
		msgBusSvr.addMethodListener("/register_webclient", "POST", this, "registerWebClient");

		/* Add listener for web browser call (start simulation) */
		msgBusSvr.addMethodListener("/start_simulation", "POST", this, "startSimulation");

		/* Source report listener */
		msgBusSvr.addMethodListener("/source_report", "POST", this, "sourceReport");

		/* Sink report listener */
		msgBusSvr.addMethodListener("/sink_report", "POST", this, "sinkReport");

		/* Proc Node report listener */
		msgBusSvr.addMethodListener("/processing_report", "POST", this, "procReport");

		/* Add listener for suspend a simulation */
		msgBusSvr.addMethodListener("/simulations", "POST", this, "stopSimulation");

		msgBusSvr.register();
		

		//TODO: Initialize the zones in better way. Currently it is hardcoded and the order is extremely imp. 
		List<String> nodeLabels = new ArrayList<String>();
		List<String> nodeTypes = new ArrayList<String>();
		nodeLabels.add("orange");
		nodeLabels.add("tomato");
		nodeLabels.add("apple");
		nodeTypes.add(WorkConfig.SOURCE_NODE_TYPE_INPUT);
		nodeTypes.add(WorkConfig.PROC_NODE_TYPE_INPUT);
		nodeTypes.add(WorkConfig.RELAY_NODE_TYPE_INPUT);
		nodeTypes.add(WorkConfig.PROC_NODE_TYPE_INPUT);
		webClientGraph.createNodeZones(nodeLabels, nodeTypes);

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
	 * 
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
	 * Master sends node create requests to NodeContainer based on the user WorkSpecification
	 * @param req
	 */
	private void createNodeOnNodeContainer(CreateNodeRequest req) {

		//TODO: Handle scenario when there is no node container available for the given label

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
	 * @param Message
	 * @param WorkConfig
	 */
	public void uploadWorkConfig(Message mesg, WorkConfig wc) {

		Set<String> srcSet = new HashSet<String>();
		Set<String> sinkSet = new HashSet<String>();
		Set<String> procSet = new HashSet<String>();
		Map<NodeType, Set<String>> nodesToInstantiate = new HashMap<NodeType, Set<String>>();

		for (Stream stream : wc.getStreamList()) {
			
			String streamId = stream.getStreamId();
			String kiloBitRate = stream.getKiloBitRate();
			String dataSize = stream.getDataSize();
			
			for (Flow flow : stream.getFlowList()) {
				
				for (Map<String, String> node : flow.getNodeList()) {

					webClientGraph.addNode(node);
					webClientGraph.addEdge(node);
					
					//TODO:A new node might have been instantiated but it hasn't registered at master
					if (!nodeNameToURITbl.containsKey(node.get(Flow.NODE_ID))) {
						String nodeType = node.get(Flow.NODE_TYPE);
						if (nodeType.equals(WorkConfig.SOURCE_NODE_TYPE_INPUT)) {
							srcSet.add(node.get(Flow.NODE_ID));
						} else if (nodeType.equals(WorkConfig.SINK_NODE_TYPE_INPUT)) {
							sinkSet.add(node.get(Flow.NODE_ID));
						} else if (nodeType.equals(WorkConfig.PROC_NODE_TYPE_INPUT)) {
							procSet.add(node.get(Flow.NODE_ID));
						}
					}
	
				}
				
				String flowId = flow.generateFlowId(streamId);
				flow.setStreamId(streamId);
				flow.setDataSize(dataSize);
				flow.setKiloBitRate(kiloBitRate);
				if (!flowMap.containsKey(flowId)) {
					flowMap.put(flowId, flow);
				}
			}
			
			if (!streamMap.containsKey(streamId)) {
				streamMap.put(streamId, stream);
			} else {
				//TODO: change to checked exception
				throw new RuntimeException("Duplicate stream ID");
			}
		}

		if (webClientURI != null) {
			try {
				msgBusSvr.send("/", webClientURI.toString() + "/create", "POST", webClientGraph.getUpdateMessage());
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}

		nodesToInstantiate.put(NodeType.SOURCE, srcSet);
		nodesToInstantiate.put(NodeType.SINK, sinkSet);
		nodesToInstantiate.put(NodeType.PROC, procSet);
		
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

		/* Iterate each nodeType */
		for (NodeType nodeType : nodesToInstantiate.keySet()) {

			/* Iterate each node in certain NodeType */
			for (String nodeId : nodesToInstantiate.get(nodeType)) {
				String nodeClass = null;

				if (nodeType == NodeType.SOURCE) {
					nodeClass = "edu.cmu.mdnsim.nodes.SourceNode";
				} else if (nodeType == NodeType.SINK) {
					nodeClass = "edu.cmu.mdnsim.nodes.SinkNode";
				} else if (nodeType == NodeType.PROC) {
					nodeClass = "edu.cmu.mdnsim.nodes.ProcessingNode";
				} else {
					//TODO: throw an exception
					nodeClass = "UNDEFINED";
				}
				//System.out.println("NodeID: "+ nodeId + ". Node Class: " + nodeClass);
				CreateNodeRequest req = new CreateNodeRequest(nodeType, nodeId, nodeClass);
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
		
		Collection<Stream> streamsToStart = streamMap.values();
		for (Stream stream : streamsToStart) {
			for (Flow flow : stream.getFlowList()) {
				String sinkUri = updateFlow(flow);
				msgBusSvr.send("/", sinkUri + "/tasks", "PUT", flow);
				runningFlowMap.put(flow.getFlowId(), flow);
			}
		}
		
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
	 * @param flow Flow to be updated
	 * @return the URI of the sink node (starting point of the control message).
	 */
	private String updateFlow(Flow flow) {

		String sinkUri = null;
		String sinkNodeId = null;

		String downStreamId = null;
		for (int i = 0; i < flow.getNodeList().size(); i++) {

			Map<String, String> nodeMap = flow.getNodeList().get(i);
			nodeMap.put("NodeUri", nodeNameToURITbl.get(nodeMap.get(Flow.NODE_ID)));
			if (i == 0) {
				sinkNodeId = nodeMap.get(Flow.NODE_ID);
				sinkUri = nodeNameToURITbl.get(sinkNodeId);
				downStreamId = nodeMap.get(Flow.NODE_ID);
				nodeMap.put("UpstreamUri", nodeNameToURITbl.get(nodeMap.get("UpstreamId")));
			} else {
				nodeMap.put("DownstreamId", downStreamId);
				nodeMap.put("DownstreamUri", nodeNameToURITbl.get(downStreamId));
				downStreamId = nodeMap.get(Flow.NODE_ID);
				nodeMap.put("UpstreamUri", nodeNameToURITbl.get(nodeMap.get("UpstreamId")));
			}

		}

		if (ClusterConfig.DEBUG) {
			assert flow.isValidFlow();
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
//		System.out.println("Sent update: " + JSON.toJSON(webClientGraph.getUpdateMessage()));
	}
	
	/**
	 * Reports sent by the Source Nodes
	 * @param request
	 * @param srcMsg
	 * @throws MessageBusException 
	 * @throws WarpException
	 */
	public void sourceReport(Message request, SourceReportMessage srcMsg) throws MessageBusException {
		String nodeId = getNodeId(request);
		String sourceNodeMsg= null;
		if(srcMsg.getEventType() == EventType.SEND_START){
			System.out.println("Source started sending data: "+JSON.toJSON(srcMsg));
			putStartTime(srcMsg.getFlowId(), srcMsg.getTime());
			sourceNodeMsg = "Started sending data for flow " + srcMsg.getFlowId() ;
			
		} else {
			sourceNodeMsg = "Done sending data for flow " + srcMsg.getFlowId() ;
		}
		//Update Node
		Node n = webClientGraph.getNode(nodeId);
		//TODO: Check if the following synchronization is required (now that we have concurrent hash map in graph)
		synchronized(n){
			n.tag = sourceNodeMsg;
		}
		//Update Edge
		Edge e = webClientGraph.getEdge(WebClientGraph.getEdgeId(nodeId,srcMsg.getDestinationNodeId()));
		
		if(e == null){
			e = webClientGraph.getEdge(WebClientGraph.getEdgeId(srcMsg.getDestinationNodeId(),nodeId));
		}
		synchronized(e){
			if(srcMsg.getEventType() == EventType.SEND_START){
				e.color = "rgb(0,255,0)";
				e.tag = "Stream Id: " + srcMsg.getFlowId();
			}else if(srcMsg.getEventType() == EventType.SEND_END){
				e.color = "rgb(255,0,0)";
				e.tag = "Stream Id: " + srcMsg.getFlowId();
			}
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
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]Master.sinkReport(): Sink finished receiving data.\n" + JSON.toJSON(sinkMsg));
		}
		String nodeId = getNodeId(request);

		if(sinkMsg.getEventType() == EventType.RECEIVE_END){
			try {
				totalTime = Utility.stringToMillisecondTime(sinkMsg.getTime()) 
						- Utility.stringToMillisecondTime(this.getStartTimeForFlow(sinkMsg.getFlowId()));
			} catch (ParseException e) {
				e.printStackTrace();
				totalTime = -1;
			}
			String sinkNodeMsg = "Done receiving data for flow " + sinkMsg.getFlowId() + " . Got " + 
					sinkMsg.getTotalBytes() + " bytes. Time Taken: " + totalTime + " ms." ;
			//Update Node
			Node n = webClientGraph.getNode(nodeId);
			synchronized(n){
				n.tag = sinkNodeMsg;
			}
		}

		//Update Edge
		Edge e = webClientGraph.getEdge(WebClientGraph.getEdgeId(nodeId,sinkMsg.getDestinationNodeId()));
		if(e == null){
			e = webClientGraph.getEdge(WebClientGraph.getEdgeId(sinkMsg.getDestinationNodeId(),nodeId));
		}
		synchronized(e){
			if(sinkMsg.getEventType() == EventType.RECEIVE_START){
				//TODO: What to do?
				/*e.color = "rgb(0,100,0)";
				e.tag = "Stream Id: " + sinkMsg.getStreamId();*/
			}else if(sinkMsg.getEventType() == EventType.RECEIVE_END){
				e.color = "rgb(255,0,0)";
				e.tag = "Stream Id: " + sinkMsg.getFlowId();
			}
		}

		if (webClientURI != null) {
			updateWebClient(webClientGraph.getUpdateMessage());
		}
	}

	public void procReport(Message request, ProcReportMessage procReport) throws MessageBusException {

		String nodeId = getNodeId(request);
		//Update Node
		if(procReport.getEventType() == EventType.RECEIVE_START){
			
			String info = String.format("[DEBUG]Master.precReport(): PROC node starts receiving");
			if (ClusterConfig.DEBUG) {
				System.out.println(info);
			}
			
			System.out.println("Processing Node started processing : "+JSON.toJSON(procReport));
			String procNodeMsg = "Processing Node started processing data for flow " + procReport.getStreamId() ;
			//Update Node
			Node n = webClientGraph.getNode(nodeId);
			//TODO: Check if the following synchronization is required (now that we have concurrent hash map in graph)
			synchronized(n){
				n.tag = procNodeMsg;
			}
		} else if (procReport.getEventType() == EventType.RECEIVE_END) {
			String info = String.format("[DEBUG]Master.procReport(): PROC node ends receiving");
			if (ClusterConfig.DEBUG) {
					System.out.println(info);
			}
		} else if (procReport.getEventType() == EventType.SEND_END) {
			String info = String.format("[DEBUG]Master.precReport(): PROC node ends sending");
			if (ClusterConfig.DEBUG) {
				System.out.println(info);
			}
		} else if (procReport.getEventType() == EventType.SEND_START) {
			String info = String.format("[DEBUG]Master.precReport(): PROC node starts sending");
			if (ClusterConfig.DEBUG) {
				System.out.println(info);
			}
		}
		//Update Edge
		Edge e = webClientGraph.getEdge(WebClientGraph.getEdgeId(nodeId,procReport.getDestinationNodeId()));
		if(e == null){
			e = webClientGraph.getEdge(WebClientGraph.getEdgeId(procReport.getDestinationNodeId(),nodeId));
		}
		synchronized(e){
			if(procReport.getEventType() == EventType.SEND_START){
				e.color = "rgb(0,255,0)";
				e.tag = "Stream Id: " + procReport.getStreamId();
			}else if(procReport.getEventType() == EventType.SEND_END){
				//TODO: What to do?
				/*e.color = "rgb(100,0,0)";
						e.tag = "Stream Id: " + procReport.getStreamId();*/
			}
		}
		if (webClientURI != null) {
			updateWebClient(webClientGraph.getUpdateMessage());
		}
	}

	public void putStartTime(String flowId, String startTime) {
		this.startTimeMap.put(flowId, startTime);
	}
	public String getStartTimeForFlow(String flowId) {
		return this.startTimeMap.get(flowId);
	}
	
	/**
	 * Stop Simulation request
	 * @throws MessageBusException
	 */
	public void stopSimulation() throws MessageBusException {

		//StopSimulationRequest req;
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]Master.stopSimulation(): Received stop "
					+ "simulation request.");
		}
		for(String flowId : runningFlowMap.keySet()) {
			Flow flow = runningFlowMap.get(flowId);
			msgBusSvr.send("/", flow.findSinkNodeURI() + "/tasks", "POST", flow);
		}

	}

	public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException, MessageBusException {
		Master mdnDomain = new Master();
		mdnDomain.init();
	}
	
	
}
