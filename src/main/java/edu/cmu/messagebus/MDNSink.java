package edu.cmu.messagebus;

import com.ericsson.research.trap.utils.ThreadPool;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpContext;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.listeners.AbstractMessageListener;
import com.ericsson.research.warp.api.message.Message;
import com.ericsson.research.warp.api.resources.Resource;

import edu.cmu.messagebus.message.PrepRcvDataMessage;

public class MDNSink extends MDNNode {
	
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
		
		Warp.addMethodListener("/sink/prep", "GET", this, "prepRcvData");
	}
	
	public void prepRcvData(PrepRcvDataMessage msg) {
		
		//TODO: Substitute 0 to sink node data transfer implementation
		int sinkPort = 0;
		String sinkIP = "";
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNSink.prepRcvData(): Open new port to receive data.");
		}
		
//		ThreadPool.executeCached(new Runnable() {
//
//			@Override
//			public void run() {
//				WarpContext.setApplication(_client);
//				new ReceiverObj(MDNSink.this)
//			}
//			
//		});
//		
//		msg.get
//		Warp.send("/", , method, body);
		
	}
	
//	public void sendReport() {
//		Warp.send(fromPath, to, method, body);
//	}
	
	public static void main(String[] args) throws WarpException {
		MDNSink sink = new MDNSink();
		sink.config();
		sink.init();
	}

}
