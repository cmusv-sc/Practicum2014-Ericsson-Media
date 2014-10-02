package edu.cmu.messagebus;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpContext;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SndDataMessage;

public class MDNSink extends MDNNode {
	
	public MDNSink() {
		super(NodeType.SINK);
	}

	@Override
	public void config() throws WarpException {
		super.config();
		
		Warp.addMethodListener("/sink/prep", "POST", this, "prepRcvData");
		
	}
	
	public void prepRcvData(PrepRcvDataMessage msg) throws WarpException {
		
		//TODO: Substitute 0 to sink node data transfer implementation
		int sinkPort = 0;
		String sinkIP = "";
		
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
		SndDataMessage sndDataMsg = new SndDataMessage(sinkIP, sinkPort);
		sndDataMsg.setDataSize(msg.getDataSize());
		sndDataMsg.setDataRate(msg.getDataSize());
		sndDataMsg.setStreamID(msg.getStreamID());
		
		WarpContext.setApplication(_client);
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNSink.prepRcvData(): Ready to receive data. Sending control message to:" + msg.getSourceWarpURI());
		}
		
		Warp.send("/source/prep", WarpURI.create(msg.getSourceWarpURI()), "POST", JSON.toJSON(sndDataMsg).getBytes());
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG] MDNSink.prepRcvData(): Open new port to receive data.");
		}
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
