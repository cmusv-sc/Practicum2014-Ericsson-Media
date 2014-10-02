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
import edu.cmu.messagebus.message.StartSimulationRequest;

public class MDNManager {
	
	//key: WarpURI, value: 
	private ConcurrentHashMap<String, NodeRegistrationRequest> _nodeTbl = 
			new ConcurrentHashMap<String, NodeRegistrationRequest>();
	
	private static WarpService _svc;
	private NamingService _namingService;
	
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
        
        _svc.register();
		
	}

	
	public static void main(String[] args) throws WarpException {
		MDNManager manager = new MDNManager();
		manager.init();
	}
	
	
	public void registerNode(Message request, NodeRegistrationRequest registMsg) throws WarpException {
		String newNodeName = MDNManager.this._namingService.nameNode(registMsg.getType());
		_nodeTbl.put(newNodeName, registMsg);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + newNodeName + " from " + request.getFrom().toString());
		}
	}
	
	public void startSimulation(StartSimulationRequest request) {
		//TODO: Store the WebClient warp uri in the hash map
		
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
	
	
	private class NamingService {
		
		private long _sourceCounter;
		private long _sinkCounter;
		
		public NamingService() {
			_sourceCounter = 0;
			_sinkCounter = 0;
		}
		
		public synchronized String nameNode(NodeType type) {
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
