package edu.cmu.mdnsim.server;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.PackageScanner;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusServer;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeContainerRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeReply;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage.Edge;
import edu.cmu.mdnsim.messagebus.message.WebClientUpdateMessage.Node;
import edu.cmu.mdnsim.nodes.NodeType;
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
	 * The _nodeTbl is a table that maps the name of MDNNode to WarpURI of 
	 * MDNNode
	 */
	private ConcurrentHashMap<String, RegisterNodeRequest> _nodeTbl = 
			new ConcurrentHashMap<String, RegisterNodeRequest>();
	
	/**
	 * The labelTbl is the table that maps the label to the node name.
	 */
	private ConcurrentHashMap<String, RegisterNodeContainerRequest> labelTbl = 
			new ConcurrentHashMap<String, RegisterNodeContainerRequest>();
	
	/**
	 * _webClientURI records the URI of the web client
	 */
	private String _webClientURI;
	
	/**
	 * _svc is the instance of WarpService
	 */
//	
	
	/**
	 * _namingService provides naming service
	 */
	private NamingService _namingService;
	private HashMap<String, String> _startTimeMap;
    
    public Master() throws MessageBusException {
    	msgBusSvr = instantiateMsgBusServer("edu.cmu.mdnsim.messagebus.MessageBusServerWarpImpl");
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
		
		_namingService = new NamingService();
		_startTimeMap = new HashMap<String, String>();

		/* Create a new node in the NodeContainer. This is called by WorkSpecification Parser.*/
		msgBusSvr.addMethodListener("/nodes", "POST", this, "createNode");

		/* Register a new node. This is called from a real Node */
		msgBusSvr.addMethodListener("/nodes", "PUT", this, "registerNode");
		
		/* Register a new node. This is called from a Node Container */
		msgBusSvr.addMethodListener("/node-containers", "PUT", this, "registerNodeContainer");
		
		/* The user specified work specification in JSON format is validated and graph JSON is generated*/
		msgBusSvr.addMethodListener("/validate_user_spec", "POST", this, "validateUserSpec");
		
		/* Once the hosted resource for the front end connects to the domain, it registers itself to the master*/
		msgBusSvr.addMethodListener("/register_webclient", "POST", this, "registerWebClient");
//
//		/* Add listener for web browser call (start simulation) */
//		msgBusSvr.addMethodListener("/start_simulation", "POST", this,
//				"startSimulation");
//
//		/* Source report listener */
//		msgBusSvr.addMethodListener("/source_report", "POST", this,
//				"sourceReport");
//
//		/* Sink report listener */
//		msgBusSvr.addMethodListener("/sink_report", "POST", this, "sinkReport");


		// _svc.register();
		msgBusSvr.register();

    }
    /**
     * Sends node creation message to appropriate node container. 
     * Node container is identified by label in node creation request.
     * @param message The incoming message for creation of node
     * @param req Associated node creation request object
     */
    public void createNode(Message message, CreateNodeRequest req) {
    	//TODO: Handle scenario when there is no node container available for the given label
    	String ncURI = labelTbl.get(req.getNcLabel()).getNcURI();
    	if (ClusterConfig.DEBUG) {
    		System.out.println("[DEBUG]Master.createNode(): To create a " + req.getNodeType() + " in label " + req.getNcLabel() + " at " + ncURI);
    		System.out.println("Class = " + req.getNodeClass());
    	}
    	
    	try {
    		msgBusSvr.send("/nodes", ncURI + "/nodes", "PUT", req);
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
		String newNodeName = this._namingService.nameNode(registMsg.getType());
		_nodeTbl.put(newNodeName, registMsg);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + newNodeName + " from " + request.getFrom().toString());
		}
		
		RegisterNodeReply reply = new RegisterNodeReply();
		reply.setNodeName(newNodeName);
		System.out.println("FROM:" + request.getFrom());
		try {
			msgBusSvr.send("/nodes", request.getFrom().toString() + "/nodename", "POST", reply);
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void registerNodeContainer(Message msg, RegisterNodeContainerRequest req) {
		String newNodeName = this._namingService.nameNode(NodeType.NODE_CONTAINER);
		req.setNodeName(newNodeName);
		req.setNcURI(msg.getFrom().toString());	
		//TODO: Do we really need to store request objects in Map? 
		labelTbl.put(req.getLabel(), req);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNodeContainer(): Register new node container:" 
					+ newNodeName + " from " + req.getNcURI());
		}
	}

	/**
	 * Method handler to handle validate user spec message.
	 * Validates user spec and creates the graph
	 * @param msg
	 * @param ws
	 */
	public void validateUserSpec(Message mesg, StreamSpec ws) {
		System.out.println("Stream Id is "+ws.StreamId);
		System.out.println("DataSize is "+ws.DataSize);
		System.out.println("ByteRate is "+ws.ByteRate);
		
		HashSet<String> nodeSet = new HashSet<String>();
		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
		ArrayList<Node> nodeList = new ArrayList<WebClientUpdateMessage.Node>();
		ArrayList<Edge> edgeList = new ArrayList<WebClientUpdateMessage.Edge>();
		double x = 0.1;
		double y = 0.1;
		String srcRgb = "rgb(0,204,0)";
		String sinkRgb = "rgb(0,204,204)";
		String srcMsg = "This is a Source Node";
		String sinkMsg = "This is a Sink Node";
		String edgeMsg = "This is Edge";
		int nodeSize = 6;
		
		for (HashMap<String, String> node : ws.Flow) {
			x = Math.random();
			y = Math.random();
			String nType = node.get("NodeType");
			String nId = node.get("NodeId");
			String upstreamNode = node.get("UpstreamId");
			String rgb = "";
			String msg = "";
			String edgeId = nId+"-"+upstreamNode;
			String edgeType = "";
			
			if (nType.equals("SOURCE")) {
				rgb = srcRgb;
				msg = srcMsg;
			} else if (nType.equals("SINK")) {
				rgb = sinkRgb;
				msg = sinkMsg;
			}
			if (!nodeSet.contains(nId)) {
				/*
				 * This node list is used to ensure that the node is added only once to the node list.
				 * This situation can arise if a sink node is connected to two upstream sources at the 
				 * same time
				 */
				nodeList.add(webClientUpdateMessage.new Node(nId, nId, x, y, rgb, nodeSize,  msg));
				nodeSet.add(nId);
			}
			if(!upstreamNode.equals("NULL"))
				edgeList.add(webClientUpdateMessage.new Edge(edgeId, upstreamNode, nId, edgeType,edgeMsg));
			System.out.println("**** Node in Flow ****");
			System.out.println("Recevied flow is "+nType +" "+ nId +" "+upstreamNode);
		}
		Node[] nodes = new Node[nodeList.size()];
		Edge[] edges = new Edge[edgeList.size()];
		nodeList.toArray(nodes);
		edgeList.toArray(edges);
		webClientUpdateMessage.setEdges(edges);
		webClientUpdateMessage.setNodes(nodes);
		
//		Node[] nodes1 = {
//				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,204,0)", 6,  "This is source node"),
//				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,204,204)", 6, "This is sink node")
//		};
//		Edge[] edges1 = {
//				webClientUpdateMessage.new Edge("E1",nodes1[0].id, nodes1[1].id, "")
//		};
//		webClientUpdateMessage.setEdges(edges1);
//		webClientUpdateMessage.setNodes(nodes1);

		try {
			Warp.send("/", WarpURI.create(_webClientURI.toString() + "/create"), "POST", 
					JSON.toJSON(webClientUpdateMessage).getBytes() );
			System.out.println("Sent update: " + JSON.toJSON(webClientUpdateMessage));
		} catch (WarpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Handler for the registration message from the web client
	 * @param msg
	 */
	public void registerWebClient(Message request, String webClientUri) {
		_webClientURI = webClientUri;
		System.out.println("Web client URI is "+_webClientURI.toString());
	}
	
	/* */
//	public void startSimulation(Message msg, StartSimulationRequest request) throws WarpException {
//		_webClientURI = msg.getFrom();
//		
//		if (ClusterConfig.DEBUG) {
//			System.out.print("[DEBUG] Master.startSimulation: web client URI:");
//			System.out.println(_webClientURI);
//		}
//		
//		String sinkNodeName = request.getSinkNodeName();
//		String sourceNodeName = request.getSourceNodeName();
//		
//		//TODO: Update WebClient with initial nodes and edges configuration as per input script
//		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
//		//Node[] nodes = (Node[]) Domain.getWebClient().getNodes().toArray();
//		Node[] nodes = {
//				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,204,0)", 6,  "This is source node"),
//				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,204,204)", 6, "This is sink node")
//		};
//		Edge[] edges = {
//				webClientUpdateMessage.new Edge("E1",nodes[0].id, nodes[1].id, "")
//		};		
//		webClientUpdateMessage.setEdges(edges);
//		webClientUpdateMessage.setNodes(nodes);
//		Warp.send("/", WarpURI.create(_webClientURI.toString() + "/create"), "POST", 
//				JSON.toJSON(webClientUpdateMessage).getBytes() );
//		
//		NodeRegistrationRequest sinkNode = this._nodeTbl.get(sinkNodeName);
//		NodeRegistrationRequest sourceNode = this._nodeTbl.get(sourceNodeName);
//		
//		String sinkResource = sinkNode.getWarpURI().toString() + "/sink/prep";		
//		String sourceResource = sourceNode.getWarpURI().toString() + "/source/snd_data";
//		
//		if (ClusterConfig.DEBUG) {
//			System.out.println("[DEBUG] Master.startSimulation(): sink node:" + sinkNodeName + "\tsink resource:" + sinkResource);
//			System.out.println("[DEBUG] Master.startSimulation(): source node:" + sourceNodeName + "\tsource resource:" + sourceResource);
//		}
//		
//		PrepRcvDataMessage prepRcvDataMsg = new PrepRcvDataMessage(sourceResource);
//		prepRcvDataMsg.setDataSize(request.getDataSize());
//		prepRcvDataMsg.setDataRate(request.getStreamRate());
//		prepRcvDataMsg.setStreamID(request.getStreamID());
//		
//		if (ClusterConfig.DEBUG) {
//			System.out.println("[DEBUG] MDNManager.startSimulation(): Receive the stimulus to start simulation.");
//		}
//		
//		try {
//			Warp.send("/", WarpURI.create(sinkResource), "POST", JSON.toJSON(prepRcvDataMsg).getBytes());
//		} catch (WarpException e) {
//			e.printStackTrace();
//		}
//	}


	private void updateWebClient(WebClientUpdateMessage webClientUpdateMessage)
			throws WarpException {
		Warp.send("/", WarpURI.create(_webClientURI.toString()+"/update"), "POST", 
				JSON.toJSON(webClientUpdateMessage).getBytes() );
	}
//	
//	public void sourceReport(Message request, SourceReportMessage srcMsg) throws WarpException {
//		System.out.println("Source finished sending data. StreamId "+srcMsg.getStreamId()+
//				" bytes transferred "+srcMsg.getTotalBytes_transferred());
//		//Warp.send("/", WarpURI.create(_webClientURI.toString()+"/update"), "POST", "simulationStarted".getBytes(),"text/plain" );
//		String sourceNodeMsg = "Done sending data for stream " + srcMsg.getStreamId() + " . Transferred " + srcMsg.getTotalBytes_transferred() + " bytes." ;
//		putStartTime(srcMsg.getStreamId(), srcMsg.getStartTime());
//		
//		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
//		Node[] nodes = {
//				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,255,0)", 6,  sourceNodeMsg),
//				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,204,204)", 6, "This is sink node")
//		};
//		Edge[] edges = {
//				webClientUpdateMessage.new Edge("E1","N1", "N2", "")
//		};
//		
//		webClientUpdateMessage.setEdges(edges);
//		webClientUpdateMessage.setNodes(nodes);
//		updateWebClient(webClientUpdateMessage);
//	}
//	
//	public void sinkReport(Message request, SinkReportMessage sinkMsg) throws WarpException {
//		long totalTime = 0;
//		DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
//		try {
//			totalTime = df.parse(sinkMsg.getEndTime()).getTime() - 
//					df.parse(getStartTimeForStream(sinkMsg.getStreamId())).getTime();
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//			totalTime = -1;
//		}
//		
//		System.out.println("Sink finished receiving data. StreamId "+sinkMsg.getStreamId()+
//				" Total bytes "+sinkMsg.getTotalBytes()+ " End Time "+sinkMsg.getEndTime());
//		
//		String sinkNodeMsg = "Done receiving data for stream " + sinkMsg.getStreamId() + " . Got " + 
//				sinkMsg.getTotalBytes() + " bytes. Time Taken: " + totalTime + " ms." ;
//		
//		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
//		Node[] nodes = {
//				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,204,0)", 6,  "This is source node"),
//				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,255,255)", 6, sinkNodeMsg)
//		};
//		Edge[] edges = {
//				webClientUpdateMessage.new Edge("E1","N1", "N2", "t")
//		};
//		
//		webClientUpdateMessage.setEdges(edges);
//		webClientUpdateMessage.setNodes(nodes);
//		updateWebClient(webClientUpdateMessage);
//	}
//	
//	public void putStartTime(String streamId, String startTime) {
//		this._startTimeMap.put(streamId, startTime);
//	}
//	public String getStartTimeForStream(String streamId) {
//		return this._startTimeMap.get(streamId);
//	}

    
    public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException, MessageBusException {
    	Master mdnDomain = new Master();
    	mdnDomain.init();
    }
    

    private class NamingService {
		
		/**
		 * The counter to track accumulatively the total nodes registering on
		 * the Master
		 */
    	private ConcurrentHashMap<NodeType, Integer> counter;
		
		
		/**
		 * The default constructor 
		 */
		public NamingService() {
			counter = new ConcurrentHashMap<NodeType, Integer>();
		}
		
		/**
		 * This method provides the naming service. The node is named by its
		 * NodeType.
		 * 
		 * @param type
		 * @return
		 */
		public synchronized String nameNode(NodeType type) {
			if (counter.containsKey(type)) {
				int val;
				//TODO: Do we need this synchronization? As we are already in synchronization block?
				synchronized(counter) {
					val = counter.get(type);
					counter.put(type, val + 1);
				}				
				return String.format("%s-%03d", type, val);
			} else {
				synchronized(counter) {
					counter.put(type,  1);
				}
				return String.format("%s-%03d", type, 1);
			}
			
		}
	}
}
