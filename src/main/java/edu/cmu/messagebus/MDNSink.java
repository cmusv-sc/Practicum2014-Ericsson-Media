package edu.cmu.messagebus;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpContext;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SndDataMessage;
import edu.cmu.nodes.MdnSinkNode;

public class MDNSink extends MDNNode {
	
	MdnSinkNode sinkNode;
	
	public MDNSink() {
		super(NodeType.SINK);
		sinkNode = new MdnSinkNode();
	}

	@Override
	public void config() throws WarpException {
		super.config();
		
		Warp.addMethodListener("/sink/prep", "POST", this, "prepRcvData");
		
	}
	
	public void prepRcvData(PrepRcvDataMessage msg) throws WarpException {
		
		final String streamId = msg.getStreamID();
		//TODO: Substitute 0 to sink node data transfer implementation
		int sinkPort = sinkNode.bindAvailablePortToStream(streamId);
		String sinkIP = sinkNode.getHostAddr();
		
		WarpThreadPool.executeCached(new Runnable() {

			@Override
			public void run() {
				MDNSink.this.sinkNode.receiveAndReport(streamId, MDNSink.this);
			}
			
		});
		
		SndDataMessage sndDataMsg = new SndDataMessage(sinkIP, sinkPort);
		sndDataMsg.setDataSize(msg.getDataSize());
		sndDataMsg.setDataRate(msg.getDataRate());
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
