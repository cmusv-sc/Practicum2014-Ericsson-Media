package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.PackageScanner;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusServer;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.StreamReportMessage;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
import edu.cmu.mdnsim.nodes.AbstractNode;
import edu.cmu.mdnsim.nodes.NodeContainer;
import edu.cmu.mdnsim.reporting.WebClientGraph;
import edu.cmu.util.Utility;
/**
 * It represents the Master Node of the Simulator.
 * Some of the major responsibilities include: 
 * 1. Communicating with WebClient - Parsing user input (work specification) and sending regular updates
 * 2. Creating and keeping track of all active nodes in the system
 * 3. Creating and keeping track of all Node Containers
 * 4. Staring Message Bus Server and registering resource for reporting
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class Master extends TimerTask {

	private static final double PACKET_LOSS_THRESHOLD = 0.16;
	private static final Object streamLatencyTrackerLock = new Object();

	static Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.master");

	/**
	 * The Message Bus Server is part of the master node and is started along with master
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
	 * Key: StreamId, Value: Stream
	 */
	private Map<String, Stream> streamMap = new ConcurrentHashMap<String, Stream>();
	/**
	 * Map of flow id's to a Flow. The flows in this map have not started yet
	 */
	private Map<String, Flow> flowMap = new ConcurrentHashMap<String, Flow>();
	private Map<String, Flow> runningFlowMap = new ConcurrentHashMap<String, Flow>();

	/**
	 * Map of flows that are flowing through a node (NodeId). Used to update the NodeUri in
	 * the flow when the node registers itself.
	 * The flow is removed from this map once a node is registered and the flow is 
	 * updated
	 */
	private Map<String, ArrayList<Flow>> flowsInNodeMap = new ConcurrentHashMap<String, ArrayList<Flow>>();

	/**
	 * Map of NodeId to node type. Used in instantiateNodes function to find class
	 * implementing the node
	 */
	private Map<String, String> nodesToInstantiate = new HashMap<String, String>();
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
		this("edu.cmu.mdnsim.messagebus.MessageBusServerRMBImpl");
	}

	public Master(String msgBusSvrClassName) throws MessageBusException {
		msgBusSvr = instantiateMsgBusServer(msgBusSvrClassName);
	}

	/**
	 * 
	 * Initialize the central master of MSNSim.
	 * <p>
	 * Specifically, it does the following things:
	 * <ul>
	 * 	<li>Starts an instance of {@link MessageBusServer}.</li>
	 * 	<li>Registers method listeners for requests from web client, {@link NodeContainer} and {@link AbstractNode}in 
	 * 		the control message layer</li>
	 * </ul>
	 * 
	 * @throws MessageBusException    
	 */
	public void init(String masterIP) throws MessageBusException {

		msgBusSvr.config(masterIP);
		
		System.out.println(msgBusSvr.getURL());

		/* Register a new node. This is called from a real Node */
		msgBusSvr.addMethodListener("/nodes", "PUT", this, "registerNode");

		msgBusSvr.addMethodListener("/nodes", "DELETE", this, "removeAllNodes");

		/* Register a new node container. This is called from a node container */
		msgBusSvr.addMethodListener("/node_containers", "PUT", this, "registerNodeContainer");

		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/work_config", "POST", this, "startWorkConfig");

		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/work_config", "DELETE", this, "stopWorkConfig");

		/* Once the hosted resource for the front end connects to the domain, it registers itself to the master*/
		msgBusSvr.addMethodListener("/register_webclient", "POST", this, "registerWebClient");

		/* Add listener for web browser call (start simulation) */
		msgBusSvr.addMethodListener("/start_simulation", "POST", this, "startSimulation");

		/* Add listener for suspend a simulation */
		msgBusSvr.addMethodListener("/simulations", "DELETE", this, "resetSimulation");		

		/* Stream report listener */
		msgBusSvr.addMethodListener("/stream_report", "POST", this, "streamReport");
		
	}


	/**
	 * Takes in the MessageBus class name used for communicating among the nodes and 
	 * initializes the MessageBusServer interface with object of that class
	 * To add a new MessageBus implementation, simply add a class to the "edu.cmu.messagebus" 
	 * package and pass that class name to this method.
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
	 * Multiple requests to create the same node on the NodeContainer may be sent. 
	 * The NodeContainer should handle multiple requests and make sure the 
	 * node is created only once. multiple requests may be sent due to 
	 * potential race conditions in updating the nodesToInstantiate Map
	 * @param req
	 */
	private void createNodeOnNodeContainer(CreateNodeRequest req) {

		//TODO: Handle scenario when there is no node container available for the given label

		String containerLabel = req.getNcLabel();
		String ncURI = nodeContainerTbl.get(containerLabel);

		logger.debug("To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " named " + req.getNodeId() + " at " + ncURI);

		try {
			msgBusSvr.send("/", ncURI + NodeContainer.NODE_COLLECTION_PATH + "/" + req.getNodeId(), "PUT", req);
		} catch (MessageBusException e) {
			logger.error(e.toString());
		}

	}

	/**
	 * The method (listener) is for accepting registration of new real node. 
	 * During the bootstrap of Node, they connect to Master to register 
	 * itself in the cluster
	 * Updates the Graph on the webclient to reflect all operational nodes
	 * and edges
	 * @param request The request message received by message bus
	 * @param registMsg The NodeRegistrationRequest which is encapsulated in 
	 * request
	 */    
	public synchronized void registerNode(RegisterNodeRequest registMsg) {

		String nodeName = registMsg.getNodeName();
		
		nodeNameToURITbl.put(nodeName, registMsg.getURI());
		nodeURIToNameTbl.put(registMsg.getURI(), nodeName);
		
		logger.debug("[DEBUG] MDNManager.registerNode(): Register new "
				+ "node:" + nodeName + " from " + registMsg.getURI());
		try {
			msgBusSvr.send("/nodes", registMsg.getURI() + "/confirm_node", "PUT", registMsg);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

		if (webClientURI != null) {
			//Synchronization is required to ensure that we get latest values of the nodeNameToURITbl map 
			//as it will be updated independently by the nodes as they come up
			//synchronized(System.in){
			try {
				WebClientUpdateMessage msg = webClientGraph.getUpdateMessage(nodeNameToURITbl.keySet());
				msgBusSvr.send("/", webClientURI.toString() + "/create", "POST", msg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
			//}
		}

		// remove the node from nodesToInstantiate Map
		this.nodesToInstantiate.remove(nodeName);


		/*
		 *  For every flow in the flowList of a nodeId (the list of flows that are waiting for a node to
		 *  register itself), update the flow with the nodeUri.
		 *  If all the nodes in a flow are up, start the flow
		 */
		if (flowsInNodeMap.containsKey(nodeName)) {
			ArrayList<Flow> flowList = new ArrayList<Flow>();
			for (Flow flow : flowsInNodeMap.get(nodeName)) {
				flow.updateFlowWithNodeUri(nodeName, registMsg.getURI());
				if (flow.canRun()) {
					//					System.out.println("Can run is true for flow "+flow.getFlowId());
					/*
					 * After updating the flow with the Uri of the node that has come up,
					 * if the flow can run, meaning if all the nodes from sink to source
					 * are up and registered with the master, then start the flow.
					 */
					this.runFlow(flow);
				} else {
					// add this flow to a list of flows that cannot run yet
					flowList.add(flow);
				}
			}
			// update the flowList with flows that cannot run yet
			flowsInNodeMap.put(nodeName, flowList);
		}

	}

	/**
	 * Run the flow by sending a message to the sink to initiate the 
	 * request for the flow from the source
	 * Remove the flow from the flowMap and add it to the runningFlowMap
	 * @param flow
	 */
	private void runFlow(Flow flow) {
		String sinkUri;
		try {
			sinkUri = flow.getSinkNodeURI();
			logger.debug("runFlow():\tStarting flow " + flow.getFlowId() + " for Stream: " + flow.getStreamId());
			msgBusSvr.send("/"+ flow.getFlowId(), sinkUri + "/tasks", "PUT", this.streamMap.get(flow.getStreamId()));
			flowMap.remove(flow.getFlowId());
			runningFlowMap.put(flow.getFlowId(), flow);
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * The method (listener) is for accepting registration of new node container.
	 * 
	 * @param msg
	 * @param req
	 */
	public void registerNodeContainer(RegisterNodeContainerRequest req) {
		if (nodeContainerTbl.containsKey(req.getLabel())) {
			logger.info("NodeContainer with label " + req.getLabel() 
					+ " already exists");
		} else {
			nodeContainerTbl.put(req.getLabel(), req.getNcURI());
			logger.info("Register new node container label:" 
					+ req.getLabel() + " from " + req.getNcURI());
		}
	}

	/**
	 * Reset the entire system
	 */
	public synchronized void resetSimulation() {
		/*
		 * Delete all the nodes on the NodeContainer's
		 */
		removeAllNodes();
		/*
		 * Reset all the Data Structures on the master node that maintains the state of the 
		 * simulation except the NodeContainer state
		 */
		nodeNameToURITbl.clear();
		nodeURIToNameTbl.clear();
		streamMap.clear();
		flowMap.clear();
		runningFlowMap.clear();
		flowsInNodeMap.clear();
		nodesToInstantiate.clear();
		

		/* reset the WebClientGraph */
		WebClientUpdateMessage resetMsg = webClientGraph.resetWebClientGraph();
		if (webClientURI != null) {
			try {
				msgBusSvr.send("/", webClientURI.toString(), "DELETE", resetMsg);
			} catch (MessageBusException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		logger.debug("[DEBUG] MDNManager.resetSimulation(): Reset Complete");
	}

	/**
	 * This method is the listener for RESET functionality
	 */
	public void removeAllNodes() {

		for (String key : nodeContainerTbl.keySet()) {
			String nodeURI = nodeContainerTbl.get(key);
			try {
				msgBusSvr.send("/", nodeURI + NodeContainer.NODE_COLLECTION_PATH, "DELETE", new MbMessage());
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Method handler to handle validate user spec message.
	 * Validates user spec and creates the graph
	 * @param Message
	 * @param WorkConfig
	 */
	public synchronized void startWorkConfig(WorkConfig wc) {
		
		logger.debug("startWorkConfig(): Receive a simulation request.");
		
		for (Stream stream : wc.getStreamList()) {
			String streamId = stream.getStreamId();
			String kiloBitRate = stream.getKiloBitRate();
			String dataSize = stream.getDataSize();
			for (Flow flow : stream.getFlowList()) {
				flow.updateFlowWithDownstreamIds();
				flow.setStreamId(streamId);
				flow.setDataSize(dataSize);
				flow.setKiloBitRate(kiloBitRate);
				String flowId = flow.generateFlowId();
				//We are adding the nodes in reverse order because nodes are created in reverse order 
				//- first sink then others and finally Source node. If the work config order changes then following code needs to be changed				
				ListIterator<Map<String, String>> nodesReverseIterator = flow.getNodeList().listIterator(flow.getNodeList().size());
				while(nodesReverseIterator.hasPrevious()){
					Map<String,String> nodeProperties = (Map<String,String>)nodesReverseIterator.previous();
					String nodeId = nodeProperties.get(Flow.NODE_ID);
					String nodeType = nodeProperties.get(Flow.NODE_TYPE);
					
					webClientGraph.addNode(nodeProperties);
					webClientGraph.addEdge(nodeProperties);
					if(!this.nodeNameToURITbl.containsKey(nodeId)) {
						//If a node in the flow is not registered yet, add it to a list of nodes to be instantiated
						//  and add it a list of pending flows waiting for a node to register itself
						nodesToInstantiate.put(nodeId, nodeType);
						ArrayList<Flow> flowList;
						if (this.flowsInNodeMap.containsKey(nodeId)) {
							// update existing flowList
							flowList = this.flowsInNodeMap.get(nodeId);
						} else {
							// create a new flowList and add it to the map
							flowList = new ArrayList<Flow>();
						}
						flowList.add(flow);
						this.flowsInNodeMap.put(nodeId, flowList);
					} else {
						/* update the flow with the nodeUri */
						flow.updateFlowWithNodeUri(nodeId, this.nodeNameToURITbl.get(nodeId));
					}
				}

				if (!flowMap.containsKey(flowId)) {
					flowMap.put(flowId, flow);
				}

				if (flow.canRun())
					this.runFlow(flow);
			}

			if (!streamMap.containsKey(streamId)) {
				streamMap.put(streamId, stream);
			} else {
				Stream existedStream = streamMap.get(streamId);
				for (Flow uploadedFlow : stream.getFlowList()) {
					if (!existedStream.containsFlowID(uploadedFlow.getFlowId())) {
						//TODO: This is just a simple addition.
						/*
						 * More strict validation is required to the added flows, such as:
						 * [1] Are bit rate and data size the same?
						 * [2] Is the added flow correctly attached to some relay the existed stream
						 */
						existedStream.addFlow(uploadedFlow);
					}

				}
			}
		}
		//Generate locations for all the nodes
		webClientGraph.setLocations();

		instantiateNodes();
	}

	/**
	 * This method (listener) is for accepting registration from web client.
	 * @param msg
	 */
	public void registerWebClient(String webClientUri) {
		System.out.println("webclient: " + webClientUri);
		webClientURI = webClientUri;
	}

	/**
	 * Issues request to NodeContainers to create nodes.
	 */
	private void instantiateNodes() {

		for (String nodeId : nodesToInstantiate.keySet()) {
			String nodeType = nodesToInstantiate.get(nodeId);
			String nodeClass = "edu.cmu.mdnsim.nodes."+nodeType;
			CreateNodeRequest req = new CreateNodeRequest(nodeType, nodeId, nodeClass);
			createNodeOnNodeContainer(req);
		}
	}

	/**
	 * Triggers the simulation by sending the Flow to the sink
	 * After a Flow is sent to the sink, it is removed from the
	 * "FlowMap" hash map and put into the runningFlowMap.
	 * This way, if the user wishes to add new flows to an already running
	 * simulation, this method will send Flow to sink of new flows
	 * 
	 * @param msg
	 * @throws WarpException
	 */

	public void startSimulation() throws MessageBusException {

		for (Flow flow : flowMap.values()) {
			String sinkUri = updateFlow(flow);
			msgBusSvr.send("/", sinkUri + "/tasks", "PUT", flow);
			runningFlowMap.put(flow.getFlowId(), flow);
		}

		if (ClusterConfig.DEBUG) {
			logger.debug("[DEBUG]Master.startSimulation(): The simulation "
					+ "has started");

		}
	}

	/**
	 * 
	 * This method is a helper method that performs 2 functions:
	 * <ul>
	 * 	<li>Maps the upstream id to its URI so the node along the chain can send the data to the destination.</li>
	 * 	<li>Automatically fills in the downstream id and downstream URI based on the relationship specified by 
	 * 	upstream.</li>
	 * </ul>
	 * 
	 * @param	flow	Flow to be updated
	 * 
	 * @return	the URI of the sink node (starting point of the control message).
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

		assert flow.isValidFlow();
		
		return sinkUri;
	}

	/**
	 * 
	 * Sends Update message (updated graph) to the web client .
	 * 
	 * @param	webClientUpdateMessage
	 * 
	 * @throws	MessageBusException 
	 */
	private void updateWebClient(WebClientUpdateMessage webClientUpdateMessage)
			throws MessageBusException {
		if(this.webClientURI != null)
			msgBusSvr.send("/", webClientURI.toString()+"/update", "POST", webClientUpdateMessage);
	}


	/**
	 * Extracts node id from messageRequest.from
	 * nodeId should be before last "/" and after last second "/" in the request.from string
	 * It will throw runtime exceptions if message is not in proper format
	 * @param request
	 * @return NodeId
	 */
	private String getNodeId(MbMessage request) {
		String nodeId = request.source();
		nodeId = nodeId.substring(0,nodeId.lastIndexOf('/'));
		nodeId = nodeId.substring(nodeId.lastIndexOf('/')+1);
		return nodeId;
	}

	/**
	 * Stop Simulation request
	 * @throws MessageBusException
	 */
	public void stopWorkConfig(WorkConfig wc) throws MessageBusException {

		if (ClusterConfig.DEBUG) {
			logger.debug("[DEBUG]Master.stopSimulation(): Received stop "
					+ "simulation request.");
		}

		for (Stream stream : wc.getStreamList()) {
			stopStream(stream);
		}

	}

	private void stopStream(Stream stream) {

		for (Flow flow : stream.getFlowList()) {
			stopFlow(flow, stream.getStreamId());
		}

	}

	private void stopFlow(Flow flow, String streamId) {

		/*
		 * A flow switch is required here as some fields of the flow submitted 
		 * by user are missing. Therefore switch to the flow in Master's memory
		 * which contains complete node map
		 * 
		 */
		flow.setStreamId(streamId);
		String flowId = flow.generateFlowId();

		/* Flow is replaced with one in running map */
		flow = runningFlowMap.get(flowId);

		assert flow.isValidFlow();

		try {
			msgBusSvr.send("/", flow.getSinkNodeURI() + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			logger.debug("Failed to send stop control message to sink node(" + flow.getSindNodeId() + ")");
		}
	}
	/**
	 * Method Listener for stream_report resource.
	 * All the reports sent by reports related to stream will be handled by this function,
	 * It updates the appropriate nodes and edges of webclientgraph object
	 * It does not update the web client immediately. It is done by separate thread.
	 * @param request
	 * @param reportMsg
	 * @throws MessageBusException
	 */
	public void streamReport(StreamReportMessage reportMsg) throws MessageBusException {
		
		
		String nodeIdOfReportSender = getNodeId(reportMsg);		
		
		String streamId = getStreamId(reportMsg);
		//logger.debug("[Stream Report] Source NodeId: " + nodeIdOfReportSender + ", Destination NodeId:"  + reportMsg.getDestinationNodeId());
		String sourceNodeId  = nodeIdOfReportSender;
		String destinationNodeId = reportMsg.getDestinationNodeId();
		String edgeColor = null;
		String logMsg = null;
		switch(reportMsg.getEventType()){
		case SEND_START:
			webClientGraph.updateNode(nodeIdOfReportSender, streamId, EventType.SEND_START, "N/A", "N/A");
			logMsg = "Started sending data for stream " + streamId;
			break;
		case SEND_END:
			logMsg = "Stopped sending data for stream " + streamId;
			webClientGraph.updateNode(nodeIdOfReportSender, streamId, EventType.SEND_END, "N/A", "N/A");
			break;
		case PROGRESS_REPORT:
			logMsg = "Stream Id: " + streamId +"\t" + 
					"Transfer Rate (Average, Current) = " + 
					String.format("%.2f",reportMsg.getAverageTransferRate()) + "," + 
					String.format("%.2f",reportMsg.getCurrentTransferRate()) + "\t" +	 
					"Packet Loss Rate (Average, Current) = " 
					+ 	String.format("%.2f",reportMsg.getAveragePacketLossRate()) + "," + 
					String.format("%.2f", reportMsg.getCurrentPacketLossRate()); 
			
			sourceNodeId  = reportMsg.getDestinationNodeId();
			destinationNodeId = nodeIdOfReportSender;
			//Make the edge red when packet loss rate is higher than a certain threshold
			//And turn is back green when it is below
			if(reportMsg.getCurrentPacketLossRate() > PACKET_LOSS_THRESHOLD){
				edgeColor = "rgb(255,0,0)";
			}else{
				edgeColor = "rgb(0,255,0)";
			}
			webClientGraph.updateEdge(sourceNodeId,destinationNodeId, streamId, edgeColor,
					reportMsg.getAveragePacketLossRate(), reportMsg.getCurrentPacketLossRate(),
					reportMsg.getAverageTransferRate(), reportMsg.getCurrentTransferRate(), 
					reportMsg.getAvrLnk2LnkPacketLatency(), reportMsg.getAvrEnd2EndPacketLatency());

			webClientGraph.updateNode(nodeIdOfReportSender, streamId, EventType.PROGRESS_REPORT, reportMsg.getCPUUsage(), reportMsg.getMemUsage());

			break;
		case RECEIVE_START:
			logMsg = "Started receiving data for stream " + streamId;
			edgeColor = "rgb(0,255,0)";
			sourceNodeId  = reportMsg.getDestinationNodeId();
			destinationNodeId = nodeIdOfReportSender;
			//Mark the edge green to indicate end of flow
			webClientGraph.updateEdge(sourceNodeId,destinationNodeId,streamId, edgeColor, EventType.RECEIVE_START);
			webClientGraph.updateNode(nodeIdOfReportSender, streamId, EventType.RECEIVE_START, "N/A", "N/A");
			break;
		case RECEIVE_END:

			webClientGraph.updateNode(nodeIdOfReportSender, streamId, EventType.RECEIVE_END, "N/A", "N/A");
			logMsg = "Stopped receiving data for stream " + streamId;
			edgeColor = "rgb(0,0,0)";
			sourceNodeId  = reportMsg.getDestinationNodeId();
			destinationNodeId = nodeIdOfReportSender;
			//Mark the edge black to indicate end of flow
			webClientGraph.updateEdge(sourceNodeId,destinationNodeId,streamId, edgeColor, EventType.RECEIVE_END);
			break;
		default:
			break;
		}

		logger.info(Utility.getFormattedLogMessage(logMsg, nodeIdOfReportSender));
	}
	/**
	 * Update the WebClientGraph periodically every second
	 */
	@Override
	public void run() {
		try {
			updateWebClient(webClientGraph.getUpdateMessage());
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}
	/**
	 * StreamId needs to be after last "/" in request.from
	 * @param request
	 * @return
	 */
	private String getStreamId(MbMessage request) {
		String streamId = request.source();
		streamId = streamId.substring(streamId.lastIndexOf('/')+1);
		return streamId;
	}

	public static void main(String[] args) throws InterruptedException, IOException, TrapException, MessageBusException {
	
		Master mdnDomain = new Master();
		mdnDomain.init(args[0]);
		//Timer used to update the web client every 1 sec
		Timer reportTimer = new Timer();
		reportTimer.scheduleAtFixedRate(mdnDomain, 0, 1000);
	}

}
