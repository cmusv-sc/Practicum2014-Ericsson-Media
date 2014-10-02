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
import com.ericsson.research.warp.api.listeners.AbstractMessageListener;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.api.resources.Resource;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.message.NodeRegistrationReply;
import edu.cmu.messagebus.message.NodeRegistrationRequest;
import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.StartSimulationRequest;

public class MDNManager {
	
	//key: WarpURI, value: 
	private ConcurrentHashMap<String, NodeRegistrationRequest> nodeTbl_ = 
			new ConcurrentHashMap<String, NodeRegistrationRequest>();
	
	private static WarpService svc;
	private long namingFactor;
	private String _managerWarpURI;
	
	public void registerNode(Message request, NodeRegistrationRequest registMsg) throws WarpException {
		String newNodeName = MDNManager.this.nameNode(registMsg.getType());
		nodeTbl_.put(newNodeName, registMsg);
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNManager.registerNode(): Register new node:" + newNodeName + " from " + request.getFrom().toString());
		}
		NodeRegistrationReply reply = new NodeRegistrationReply(_managerWarpURI); 
		Warp.reply(request, 200, JSON.toJSON(reply).getBytes());
	}
	
	public void init() throws WarpException {
        
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp", "com.ericsson");
        
		svc = Warp.init().service(MDNManager.class.getName(), "cmu-sv", "mdn-manager")
        		.setDescriptorProperty(ServicePropertyName.LOOKUP_SERVICE_ENDPOINT,"ws://localhost:9999").create();
        
        svc.notifications().registerForNotification(Notifications.Registered, new Listener() {
            
            @Override
            public void receiveNotification(String name, Object sender, Object attachment)
            {
                WarpLogger.info("Now registered...");
                
                /* Subscribe the "/discover" pubsub channel */
//                try {
//					Warp.send("/discover", WarpURI.create("warp://warp:pubsub/public/discover"), "PUT", null);
//				} catch (WarpException e) {
//					e.printStackTrace();
//				}
            }
        }, true);
        
        /* Register the discover channel to collect new nodes */
        Warp.addMethodListener("/discover", "POST", this, "registerNode");
        
        /* Web browser calls */
        Warp.resourceAt("/start_simulation").addMethodListener("POST", new AbstractMessageListener() {

			@Override
			public boolean receiveMessage(Message message, Resource resource) {
				
				StartSimulationRequest request = JSON.fromJSON(message.getDataAsUtfString(), StartSimulationRequest.class);
				
				String sinkNodeName = request.getSinkNodeName();
				String sourceNodeName = request.getSourceNodeName();
				
				NodeRegistrationRequest sinkNode = MDNManager.this.nodeTbl_.get(sinkNodeName);
				NodeRegistrationRequest sourceNode = MDNManager.this.nodeTbl_.get(sourceNodeName);
				
				//TODO: Check the sink resource
				String sinkResource = sinkNode.getWarpURI().toString() + "/sink/prep_rcv";
				
				//TODO: Check the source resource
				String sourceResource = sourceNode.getWarpURI().toString() + "/source/snd_data";
				
				PrepRcvDataMessage prepRcvDataMsg = new PrepRcvDataMessage(sourceResource);
				prepRcvDataMsg.setDataSize(request.getDataSize());
				prepRcvDataMsg.setStreamRate(request.getStreamRate());
				prepRcvDataMsg.setStreamID(request.getStreamID());

				
				try {
					Warp.send("/", WarpURI.create(sinkResource), "POST", JSON.toJSON(prepRcvDataMsg).getBytes());
				} catch (WarpException e) {
					e.printStackTrace();
				}
				
				_managerWarpURI = Warp.uri().toString();
				
				return true;
			}
        });
        
        svc.register();
		
	}

	
	public static void main(String[] args) throws WarpException {
		MDNManager manager = new MDNManager();
		manager.init();
	}
	
	private synchronized String nameNode(NodeType type) {
		this.namingFactor++;
		if (type == NodeType.SOURCE) {
			return "source-" + this.namingFactor;
		} else if (type == NodeType.SINK){
			return "sink-" + this.namingFactor;
		} else if (type == NodeType.WEBCLIENT){
			return "webclient-" + this.namingFactor;
		}else{
			return "VOID";
		}
	}
}
