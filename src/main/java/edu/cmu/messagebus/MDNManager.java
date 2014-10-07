package edu.cmu.messagebus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpService;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.configuration.ServicePropertyName;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.message.NodeRegistrationRequest;
import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SinkReportMessage;
import edu.cmu.messagebus.message.SourceReportMessage;
import edu.cmu.messagebus.message.StartSimulationRequest;

/**
 * MDNManager works as the master in the distributed systems. The MDNManager
 * provides several functionalities:
 * [1]Registration: MDNNode needs to register in MDNManager in order to join
 * the cluster. 
 * [2]Naming service: Providing human-friendly naming service to each MDNNode.
 * [3]Control Simulation: It accepts the simulation request from web interface
 * and coordinate cluster starts to transfer data.
 *  
 * @author JeremyFu
 *
 */
public class MDNManager {
	
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
	private static WarpService _svc;
	
	/**
	 * _namingService provides naming service
	 */
	private NamingService _namingService;
	
	/**
	 * Initialization of MDNManger. Specifically, it registers itself with 
	 * Warp domain and obtain the WarpURI. Warp provides straightforward WarpURI
	 * for service("warp://provider_name:service_name"); It also registers some
	 * method listener to handle requests from Web interface and MDNNode in the
	 * control message layer
	 * 
	 * @throws WarpException
	 */
	public void init() throws WarpException {
        
		_namingService = new NamingService();
		
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp", "com.ericsson");
        
		_svc = Warp.init().service(MDNManager.class.getName(), "cmu-sv", "mdn-manager")
        		.setDescriptorProperty(ServicePropertyName.LOOKUP_SERVICE_ENDPOINT,"ws://localhost:9999").create();
        
        _svc.notifications().registerForNotification(Notifications.Registered, new Listener() {
            
            @Override
            public void receiveNotification(String name, Object sender, Object attachment) {
                WarpLogger.info("Now registered...");
            }
        }, true);
        
        /* Register the discover channel to collect new nodes */
        Warp.addMethodListener("/discover", "POST", this, "registerNode");
        
        /* Add listener for web browser call (start simulation) */
        Warp.addMethodListener("/start_simulation", "POST", this, "startSimulation");
        
        /* Source report listener */
        Warp.addMethodListener("/source_report", "POST", this, "sourceReport");
        
        /* Sink report listener */
        Warp.addMethodListener("/sink_report", "POST", this, "sinkReport");

        _svc.register();
		
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
		String newNodeName = MDNManager.this._namingService.nameNode(registMsg.getType());
		_nodeTbl.put(newNodeName, registMsg);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + newNodeName + " from " + request.getFrom().toString());
		}
	}
	
	public void startSimulation(Message msg, StartSimulationRequest request) {
		_webClientURI = msg.getFrom();
		
		System.out.println(_webClientURI);
		String sinkNodeName = request.getSinkNodeName();
		String sourceNodeName = request.getSourceNodeName();
		
		NodeRegistrationRequest sinkNode = MDNManager.this._nodeTbl.get(sinkNodeName);
		NodeRegistrationRequest sourceNode = MDNManager.this._nodeTbl.get(sourceNodeName);
		
		String sinkResource = sinkNode.getWarpURI().toString() + "/sink/prep";
		
		String sourceResource = sourceNode.getWarpURI().toString() + "/source/snd_data";
		
		PrepRcvDataMessage prepRcvDataMsg = new PrepRcvDataMessage(sourceResource);
		prepRcvDataMsg.setDataSize(request.getDataSize());
		prepRcvDataMsg.setDataRate(request.getStreamRate());
		prepRcvDataMsg.setStreamID(request.getStreamID());
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.startSimulation(): Receive the stimulus to start simulation.");
		}
		
		try {
			Warp.send("/", WarpURI.create(sinkResource), "POST", JSON.toJSON(prepRcvDataMsg).getBytes());
		} catch (WarpException e) {
			e.printStackTrace();
		}
	}
	
	public void sourceReport(Message request, SourceReportMessage srcMsg) throws WarpException {
		System.out.println("Source finished sending data. StreamId "+srcMsg.getStreamId()+
				" bytes transferred "+srcMsg.getTotalBytes_transferred());
	}
	
	public void sinkReport(Message request, SinkReportMessage sinkMsg) throws WarpException {
		System.out.println("Sink finished receiving data.. StreamId "+sinkMsg.getStreamId()+
				" Total bytes "+sinkMsg.getTotalBytes()+ " Total Time "+sinkMsg.getTotalTime());
	}
	
	/**
	 * The driver to start a MDNManger
	 * 
	 * @param args
	 * @throws WarpException
	 */
	public static void main(String[] args) throws WarpException {
		MDNManager manager = new MDNManager();
		manager.init();
	}
	
	private class NamingService {
		
		/**
		 * The counter to track the accumulative Source Node registering on
		 * the MDNManager
		 */
		private long _sourceCounter;
		
		/**
		 * The counter to track the accumulative Sink Node registering on 
		 * The MDNManager
		 */
		private long _sinkCounter;
		
		/**
		 * The default constructor 
		 */
		public NamingService() {
			_sourceCounter = 0;
			_sinkCounter = 0;
		}
		
		/**
		 * This method provides the naming service. The node is named by its
		 * NodeType.
		 * 
		 * 
		 * @param type
		 * @return
		 */
		public synchronized String nameNode(NodeType type) {
			/*
			 * TODO: Based client's requirement, each MDNNode should be regarded
			 * as with full functionality. Therefore, the naming service should
			 * be NodeType independent.
			 * 
			 */
			if (type == NodeType.SOURCE) {
				_sourceCounter++;
				return "source-" + _sourceCounter;
			} else if (type == NodeType.SINK) {
				_sinkCounter++;
				return "sink-" + _sinkCounter;
			} else {
				return "";
			}
		}
	}
}
