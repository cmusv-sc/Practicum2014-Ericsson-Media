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

import edu.cmu.messagebus.message.NodeRegistrationMessage;
import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.StartSimulationRequest;

public class MDNManager {
	
	//key: WarpURI, value: 
	private ConcurrentHashMap<String, NodeRegistrationMessage> nodeTbl_ = 
			new ConcurrentHashMap<String, NodeRegistrationMessage>();
	
	private static WarpService svc;
	private long namingFactor;
	
	public void onDiscover(NodeRegistrationMessage registMsg) {
		nodeTbl_.put(MDNManager.this.nameNode(registMsg.getType()), registMsg);
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
                try {
					Warp.send("/discover", WarpURI.create("warp://warp:pubsub/public/discover"), "PUT", null);
				} catch (WarpException e) {
					e.printStackTrace();
				}
            }
        }, true);
        
        /* Register the discover channel to collect new nodes */
        Warp.addMethodListener("/discover", "POST", this, "onDiscover");
        /*Warp.resourceAt("/discover").addMethodListener("POST", new AbstractMessageListener() {
    		@Override
    		public boolean receiveMessage(Message message, Resource resource)
    		{	
    			NodeRegistrationMessage registMsg = 
    					JSON.fromJSON(new String(message.getData()), NodeRegistrationMessage.class);
    			
    			nodeTbl_.put(MDNManager.this.nameNode(registMsg.getType()), registMsg);
    			
        		return true;
    		}
		});*/
        
        /* Web browser calls */
        Warp.resourceAt("/start_simulation").addMethodListener("POST", new AbstractMessageListener() {

			@Override
			public boolean receiveMessage(Message message, Resource resource) {
				
				StartSimulationRequest request = JSON.fromJSON(message.getDataAsUtfString(), StartSimulationRequest.class);
				
				String sinkNodeName = request.getSinkNodeName();
				String sourceNodeName = request.getSourceNodeName();
				
				NodeRegistrationMessage sinkNode = MDNManager.this.nodeTbl_.get(sinkNodeName);
				NodeRegistrationMessage sourceNode = MDNManager.this.nodeTbl_.get(sourceNodeName);
				
				//TODO: Check the sink resource
				String sinkResource = sinkNode.getWarpURI().toString() + "/retrive_data";
				
				//TODO: Check the source resource
				String sourceResource = sourceNode.getWarpURI().toString() + "send_data";
				
				PrepRcvDataMessage requestMsg = new PrepRcvDataMessage(sinkNode.getWarpURI());

				
				try {
					Warp.send("/", WarpURI.create(sinkResource), "POST", JSON.toJSON(requestMsg).getBytes());
				} catch (WarpException e) {
					e.printStackTrace();
				}
				return true;
			}
        });
        
        Warp.resourceAt("/").addMethodListener("GET", new AbstractMessageListener() {

			@Override
			public boolean receiveMessage(Message message, Resource resource) {
				
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
		} else {
			return "sink-" + this.namingFactor;
		}
	}
}
