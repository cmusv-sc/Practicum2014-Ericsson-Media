package edu.cmu.messagebus;

import java.io.IOException;
import java.util.logging.Level;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpInit.DomainInit;
import com.ericsson.research.warp.api.WarpInit.DomainInit.BuiltinService;

public class Domain {
    
    private static WarpDomain _warpDomain;
    
    public Domain() {
    	super();
    }
    
    static WarpDomain getWarpDomain(){
    	return _warpDomain;
    }
    public void init() throws WarpException, IOException, TrapException{
    	 JDKLoggerConfig.initForPrefixes(Level.INFO, "warp", "com.ericsson");
         DomainInit domainInit = Warp.init().domain();
         
         // Configure the gateway (client connections) to go to http://127.0.0.1:8888 as initial connection
         domainInit.getClientNetworkCfg().setBindHost("127.0.0.1").setBindPort("http", 8888).setBindPort("websocket", 8889).finish();
         
         // Configure the lookup service (service registry) to bind to http://127.0.0.1:9999 as initial connection
         domainInit.getServiceNetworkCfg(BuiltinService.LOOKUP_SERVICE).setBindHost("127.0.0.1").setBindPort("websocket", 9999).finish();
         
         // Add any additional (built-in servers) in the com.ericsson.research.warp.spi.enabled package and start
         _warpDomain = domainInit.loadWarpEnabled(true).create();
         
         System.out.println(_warpDomain.getTestClientURI());
         
         //Load the WebClient
         WebClient webClient = new WebClient();
         webClient.load(_warpDomain);
    }
    
    public static void main(String[] args) throws WarpException, InterruptedException, IOException, TrapException {
    	Domain mdnDomain = new Domain();
    	mdnDomain.init();
    }
    
}