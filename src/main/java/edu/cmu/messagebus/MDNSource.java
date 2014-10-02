package edu.cmu.messagebus;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.listeners.AbstractMessageListener;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.api.resources.Resource;

public class MDNSource extends MDNNode {
	
	@Override
	public void config() throws WarpException {
		super.config();
		
		/* Retrieve data from the source */
		Warp.resourceAt("/retrieve_data").addMethodListener("GET", new AbstractMessageListener() {
    		@Override
    		public boolean receiveMessage(Message message, Resource resource) {
    			System.err.println("UNICAST#" + message.getDataAsUtfString());
        		return true;
    		}
		});
	}
	
}
