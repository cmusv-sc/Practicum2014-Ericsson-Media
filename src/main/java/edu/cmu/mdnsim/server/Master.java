package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
import edu.cmu.mdnsim.server.WebClientGraph.Edge;
import edu.cmu.mdnsim.server.WebClientGraph.Node;
import edu.cmu.util.HtmlTags;
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
	Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.master");
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

	private Map<String, String> startTimeMap = new ConcurrentHashMap<String, String>();

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
	 * Indicates whether a graph has been created in the web client 
	 */
	boolean graphCreated = false;

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

		if (ClusterConfig.DEBUG) {
			logger.debug("[DEBUG]Master.createNode(): To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " at " + ncURI);
		}

		try {
			msgBusSvr.send("/", ncURI + "/create_node", "PUT", req);
		} catch (MessageBusException e) {
			e.printStackTrace();
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
	public void registerNode(Message request, RegisterNodeRequest registMsg) {

		String nodeName = registMsg.getNodeName();
		nodeNameToURITbl.put(nodeName, registMsg.getURI());
		nodeURIToNameTbl.put(registMsg.getURI(), nodeName);
		if (ClusterConfig.DEBUG) {
			logger.debug("[DEBUG] MDNManager.registerNode(): Register new "
					+ "node:" + nodeName + " from " + registMsg.getURI());
		}
		try {
			msgBusSvr.send("/nodes", registMsg.getURI()+"/confirm_node", "PUT", registMsg);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

		if (webClientURI != null) {
			//Synchronization is required to ensure that we get latest values of the nodeNameToURITbl map 
			//as it will be updated independently by the nodes as they come up
			synchronized(System.in){
				try {
					WebClientUpdateMessage msg = webClientGraph.getUpdateMessage(nodeNameToURITbl.keySet());
					msgBusSvr.send("/", webClientURI.toString() + "/create", "POST", msg);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			}
		}

		// remove the node from nodesToInstantiate Map
		this.nodesToInstantiate.remove(nodeName);

		synchronized(this.flowsInNodeMap) {
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
			msgBusSvr.send("/", sinkUri + "/tasks", "PUT", flow);
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
	public void registerNodeContainer(Message msg, RegisterNodeContainerRequest req) {
		if (nodeContainerTbl.containsKey(req.getLabel())) {
			if (ClusterConfig.DEBUG) {
				logger.debug("[DEBUG] MDNManager.registerNodeContainer(): "
						+ "NodeContainer with label " + req.getLabel() 
						+ " already exists");
			}
		} else {
			nodeContainerTbl.put(req.getLabel(), req.getNcURI());
			if (ClusterConfig.DEBUG) {
				logger.debug("[DEBUG] MDNManager.registerNodeContainer(): "
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
	public synchronized void uploadWorkConfig(Message mesg, WorkConfig wc) {

		for (Stream stream : wc.getStreamList()) {

			String streamId = stream.getStreamId();
			String kiloBitRate = stream.getKiloBitRate();
			String dataSize = stream.getDataSize();

			for (Flow flow : stream.getFlowList()) {
				String flowId = flow.generateFlowId(streamId);
				flow.setStreamId(streamId);
				flow.setDataSize(dataSize);
				flow.setKiloBitRate(kiloBitRate);
				flow.updateFlowWithDownstreamIds();
				//We are adding the nodes in reverse order because nodes are created in reverse order 
				//- first sink then others and finally Source node. If the work config order changes then following code needs to be changed				
				ListIterator<Map<String, String>> nodesReverseIterator = flow.getNodeList().listIterator(flow.getNodeList().size());
				while(nodesReverseIterator.hasPrevious()){
					Map<String,String> nodeProperties = (Map<String,String>)nodesReverseIterator.previous();
					String nodeId = nodeProperties.get("NodeId");
					String nodeType = nodeProperties.get("NodeType");
					webClientGraph.addNode(nodeProperties);
					webClientGraph.addEdge(nodeProperties);
					if(!this.nodeNameToURITbl.containsKey(nodeId)) {
						/*  If a node in the flow is not registered yet,
						 *  add it to a list of nodes to be instantiated
						 *  and add it a list of pending flows waiting 
						 *  for a node to register itself
						 */
						nodesToInstantiate.put(nodeId, nodeType);

						synchronized(this.flowsInNodeMap) {
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
						}
					} else {
						/* update the flow with the nodeUri */
						flow.updateFlowWithNodeUri(nodeId, this.nodeNameToURITbl.get(nodeId));
					}
				}

				if (!flowMap.containsKey(flowId)) {
					flowMap.put(flowId, flow);
				}

				/* If the flow is ready to run, i.e. all the nodes in the flow
				 * are registered with the master, then start the flow
				 */
				if (flow.canRun())
					this.runFlow(flow);
			}

			if (!streamMap.containsKey(streamId)) {
				streamMap.put(streamId, stream);
			} else {
				//TODO: change to checked exception
				//throw new RuntimeException("Duplicate stream ID");
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
	public void registerWebClient(Message request, String webClientUri) {
		webClientURI = webClientUri;
		logger.debug("Web client URI is "+webClientURI.toString());
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

	public void startSimulation(Message msg) throws MessageBusException {

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
		if(this.webClientURI != null)
			msgBusSvr.send("/", webClientURI.toString()+"/update", "POST", webClientUpdateMessage);
	}

	/**
	 * Reports sent by the Source Nodes
	 * @param request
	 * @param srcMsg
	 * @throws MessageBusException 
	 * @throws WarpException
	 */
	public synchronized void sourceReport(Message request, SourceReportMessage srcMsg) throws MessageBusException {
		String nodeId = getNodeId(request);
		String nodeMsg = null;
		String edgeColor = null;
		String edgeMsg = null;
		String logMsg = null;
		//TODO: Change node and edge messages to have a table with different streams and their status
		if(srcMsg.getEventType() == EventType.SEND_START){
			putStartTime(srcMsg.getFlowId(), srcMsg.getTime());
			logMsg = nodeMsg = "Started sending data for flow " + srcMsg.getFlowId() ;
			edgeColor = "rgb(0,255,0)";
			edgeMsg = "Started Flow Id: " + srcMsg.getFlowId();
		} else { //SEND_END event
			logMsg  = nodeMsg = "Done sending data for flow " + srcMsg.getFlowId() ;
			edgeColor = "rgb(0,0,0)";
			edgeMsg = "Ended Flow Id: " + srcMsg.getFlowId();
		}
		logger.info(Utility.getFormattedLogMessage(logMsg, nodeId));
		
		webClientGraph.updateNode(nodeId, nodeMsg);
		webClientGraph.updateEdge(nodeId,srcMsg.getDestinationNodeId(), edgeMsg, edgeColor);

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
	 * @throws MessageBusException 
	 */
	public synchronized void sinkReport(Message request, SinkReportMessage sinkMsg) throws MessageBusException {
		long totalTime = 0;
		String nodeId = getNodeId(request);
		String nodeMsg = null;
		String edgeColor = null;
		String edgeMsg = null;
		String logMsg = null;
		if(sinkMsg.getEventType() == EventType.RECEIVE_START){
			logMsg = nodeMsg = "Started receiving data for flow " + sinkMsg.getFlowId() + " . Got " + 
					sinkMsg.getTotalBytes() + " bytes." ;
			edgeColor = "rgb(0,255,0)";
			edgeMsg =  "Flow Id: " + sinkMsg.getFlowId();
		}else if(sinkMsg.getEventType() == EventType.RECEIVE_END){
			try {
				totalTime = Utility.stringToMillisecondTime(sinkMsg.getTime()) 
						- Utility.stringToMillisecondTime(this.getStartTimeForFlow(sinkMsg.getFlowId()));
			} catch (ParseException e) {
				e.printStackTrace();
				totalTime = -1;
			}
			logMsg = nodeMsg  = "Done receiving data for flow " + sinkMsg.getFlowId() + " . Got " + 
					sinkMsg.getTotalBytes() + " bytes. Time Taken: " + totalTime + " ms." ;
			edgeColor = "rgb(0,0,0)";
			edgeMsg = "Flow Id: " + sinkMsg.getFlowId();
		}else if (sinkMsg.getEventType() == EventType.PROGRESS_REPORT) {
			logMsg = edgeMsg = "Flow Id: " + sinkMsg.getFlowId() + HtmlTags.BR + 
					"Average Rate = " + sinkMsg.getAverageRate() + HtmlTags.BR + 
					"Current Rate = " + sinkMsg.getCurrentRate();
		}
		logger.info(Utility.getFormattedLogMessage(logMsg, nodeId));
		
		webClientGraph.updateNode(nodeId, nodeMsg);
		webClientGraph.updateEdge(sinkMsg.getDestinationNodeId(), nodeId, edgeMsg, edgeColor);

		updateWebClient(webClientGraph.getUpdateMessage());
	}

	public synchronized void procReport(Message request, ProcReportMessage procReport) throws MessageBusException {
		String nodeId = getNodeId(request);
		String logMsg = null;
		String nodeMsg = null;
		String edgeMsg = null;
		String edgeColor = null;
		if(procReport.getEventType() == EventType.RECEIVE_START){
			logMsg = String.format("Master.precReport(): PROC node starts receiving (FLOW ID=%s)", procReport.getFlowId());
			nodeMsg = "Processing Node started processing data for flow " + procReport.getFlowId() ;
			webClientGraph.updateNode(nodeId,nodeMsg);
			updateWebClient(webClientGraph.getUpdateMessage());
		} else if (procReport.getEventType() == EventType.RECEIVE_END) {
			logMsg = String.format("Master.procReport(): PROC node ends receiving for flow: " + procReport.getFlowId());
		} else if (procReport.getEventType() == EventType.SEND_END) {
			logMsg = String.format("Master.precReport(): PROC node ends sending for flow: " + procReport.getFlowId());;
		} else if (procReport.getEventType() == EventType.SEND_START) {
			logMsg = String.format("Processing node starts sending for flow: " + procReport.getFlowId());
			edgeMsg = "Started Flow Id: " + procReport.getFlowId();
			edgeColor = "rgb(0,255,0)";
			webClientGraph.updateEdge(nodeId, procReport.getDestinationNodeId(), edgeMsg, edgeColor);
			updateWebClient(webClientGraph.getUpdateMessage());
		} else{
			logMsg = String.format("Master.precReport(): PROC node starts receiving");
			//if (procReport.getEventType() == EventType.PROGRESS_REPORT) {
			edgeMsg = "Flow Id: " + procReport.getFlowId() + HtmlTags.BR + 
					"Average Rate = " + procReport.getAverageRate() + HtmlTags.BR +	 
					"Current Rate = " + procReport.getCurrentRate();
			webClientGraph.updateEdge(procReport.getDestinationNodeId(),nodeId, edgeMsg);
			updateWebClient(webClientGraph.getUpdateMessage());
		}
		logger.info(logMsg);
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

		if (ClusterConfig.DEBUG) {
			logger.debug("[DEBUG]Master.stopSimulation(): Received stop "
					+ "simulation request.");
		}
		for(String flowId : runningFlowMap.keySet()) {
			Flow flow = runningFlowMap.get(flowId);
			msgBusSvr.send("/", flow.getSinkNodeURI() + "/tasks", "POST", flow);
		}

	}

	public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException, MessageBusException {
		Master mdnDomain = new Master();
		mdnDomain.init();
	}


}
