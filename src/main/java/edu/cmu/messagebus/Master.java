package edu.cmu.messagebus;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpService;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.WarpInit.DomainInit;
import com.ericsson.research.warp.api.WarpInit.DomainInit.BuiltinService;
import com.ericsson.research.warp.api.configuration.ServicePropertyName;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.global.ClusterConfig;
import edu.cmu.messagebus.message.NodeRegistrationRequest;
import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SinkReportMessage;
import edu.cmu.messagebus.message.SourceReportMessage;
import edu.cmu.messagebus.message.StartSimulationRequest;
import edu.cmu.messagebus.message.WebClientUpdateMessage;
import edu.cmu.messagebus.message.WebClientUpdateMessage.Edge;
import edu.cmu.messagebus.message.WebClientUpdateMessage.Node;

public class Master {
    
    MessageBusServer msgBusSvr;
    
    /**
     * A TrapHostable that hosts the Web Interface for the Mdn Simulator
     */
    private static WebClient _webClient;
    
    /**
	 * The _nodeTbl is a table that maps the name of MDNNode to WarpURI of 
	 * MDNNode
	 */
	private ConcurrentHashMap<String, NodeRegistrationRequest> _nodeTbl = 
			new ConcurrentHashMap<String, NodeRegistrationRequest>();
	
	/**
	 * _webClientURI records the WarpURI of the web client
	 */
	private WarpURI _webClientURI;
	
	/**
	 * _svc is the instance of WarpService
	 */
//	
	
	/**
	 * _namingService provides naming service
	 */
	private NamingService _namingService;
	private HashMap<String, String> _startTimeMap;
    
    public Master() {
    	super();
    }
    
//    static WarpDomain getWarpDomain(){
//    	return _warpDomain;
//    }
    
//    static WebClient getWebClient(){
//    	return _webClient;
//    }
    
    
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
	 */
    public void init() throws WarpException, IOException, TrapException{
         
    	msgBusSvr.config();
    	
         _namingService = new NamingService();
 		_startTimeMap = new HashMap<String, String>();
 		
 		msgBusSvr.addMethodListener("/create-node", "PUT", this, "createNode");
 		
         /* Register the discover channel to collect new nodes */
         msgBusSvr.addMethodListener("/discover", "POST", this, "registerNode");
         
         /* Add listener for web browser call (start simulation) */
         msgBusSvr.addMethodListener("/start_simulation", "POST", this, "startSimulation");
         
         /* Source report listener */
         msgBusSvr.addMethodListener("/source_report", "POST", this, "sourceReport");
         
         /* Sink report listener */
         msgBusSvr.addMethodListener("/sink_report", "POST", this, "sinkReport");

//         _svc.register();
         msgBusSvr.register();
         
       //Load the WebClient
         _webClient = new WebClient();
         _webClient.load(_warpDomain);
    }
	
	/**
	 * The method listener for initialization of MDNNode. During the bootstrap 
	 * of MDNNodes, they connect to MDNManager to register itself in the cluster
	 * 
	 * @param request The request message received by message bus
	 * @param registMsg The NodeRegistrationRequest which is encapsulated in 
	 * request
	 */
    
	public void registerNode(Message request, NodeRegistrationRequest registMsg) {
		
		String newNodeName = this._namingService.nameNode();
		_nodeTbl.put(newNodeName, registMsg);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + newNodeName + " from " + request.getFrom().toString());
		}
		
		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
		WebClientUpdateMessage.Node newNode = webClientUpdateMessage.new Node(newNodeName, newNodeName, 
				Math.random(), Math.random(), "rgb(0,204,0)", 6, "TO BE CHANGED");
		//Domain.getWebClient().addNode(newNode);
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
	
	public void sourceReport(Message request, SourceReportMessage srcMsg) throws WarpException {
		System.out.println("Source finished sending data. StreamId "+srcMsg.getStreamId()+
				" bytes transferred "+srcMsg.getTotalBytes_transferred());
		//Warp.send("/", WarpURI.create(_webClientURI.toString()+"/update"), "POST", "simulationStarted".getBytes(),"text/plain" );
		String sourceNodeMsg = "Done sending data for stream " + srcMsg.getStreamId() + " . Transferred " + srcMsg.getTotalBytes_transferred() + " bytes." ;
		putStartTime(srcMsg.getStreamId(), srcMsg.getStartTime());
		
		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
		Node[] nodes = {
				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,255,0)", 6,  sourceNodeMsg),
				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,204,204)", 6, "This is sink node")
		};
		Edge[] edges = {
				webClientUpdateMessage.new Edge("E1","N1", "N2", "")
		};
		
		webClientUpdateMessage.setEdges(edges);
		webClientUpdateMessage.setNodes(nodes);
		updateWebClient(webClientUpdateMessage);
	}
	
	public void sinkReport(Message request, SinkReportMessage sinkMsg) throws WarpException {
		long totalTime = 0;
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		try {
			totalTime = df.parse(sinkMsg.getEndTime()).getTime() - 
					df.parse(getStartTimeForStream(sinkMsg.getStreamId())).getTime();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			totalTime = -1;
		}
		
		System.out.println("Sink finished receiving data. StreamId "+sinkMsg.getStreamId()+
				" Total bytes "+sinkMsg.getTotalBytes()+ " End Time "+sinkMsg.getEndTime());
		
		String sinkNodeMsg = "Done receiving data for stream " + sinkMsg.getStreamId() + " . Got " + 
				sinkMsg.getTotalBytes() + " bytes. Time Taken: " + totalTime + " ms." ;
		
		WebClientUpdateMessage webClientUpdateMessage = new WebClientUpdateMessage();
		Node[] nodes = {
				webClientUpdateMessage.new Node("N1", "source-1", 0.1, 0.1, "rgb(0,204,0)", 6,  "This is source node"),
				webClientUpdateMessage.new Node("N2", "sink-1", 0.5, 0.5, "rgb(0,255,255)", 6, sinkNodeMsg)
		};
		Edge[] edges = {
				webClientUpdateMessage.new Edge("E1","N1", "N2", "t")
		};
		
		webClientUpdateMessage.setEdges(edges);
		webClientUpdateMessage.setNodes(nodes);
		updateWebClient(webClientUpdateMessage);
	}
	
	public void putStartTime(String streamId, String startTime) {
		this._startTimeMap.put(streamId, startTime);
	}
	public String getStartTimeForStream(String streamId) {
		return this._startTimeMap.get(streamId);
	}
    
    public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException {
    	Master mdnDomain = new Master();
    	mdnDomain.init();
    }
    

    private class NamingService {
		
		/**
		 * The counter to track accumulatively the total nodes registering on
		 * the Master
		 */
		private long _counter;
		
		
		/**
		 * The default constructor 
		 */
		public NamingService() {
			_counter = 0;
		}
		
		/**
		 * This method provides the naming service. The node is named by its
		 * NodeType.
		 * 
		 * @param type
		 * @return
		 */
		public synchronized String nameNode() {
			/*
			 * TODO: Based client's requirement, each MDNNode should be regarded
			 * as with full functionality. Therefore, the naming service should
			 * be NodeType independent.
			 * 
			 */
			
			return String.format("node-%04d", _counter++);
		}
	}
}