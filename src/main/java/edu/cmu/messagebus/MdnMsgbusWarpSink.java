package edu.cmu.messagebus;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpContext;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.messagebus.message.PrepRcvDataMessage;
import edu.cmu.messagebus.message.SinkReportMessage;
import edu.cmu.messagebus.message.SndDataMessage;
import edu.cmu.nodes.MdnSinkNode;

public class MdnMsgbusWarpSink extends MdnMsgbusWarpNode {
	
	MdnSinkNode sinkNode;
	
	public MdnMsgbusWarpSink() {
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
				MdnMsgbusWarpSink.this.sinkNode.receiveAndReport(streamId, MdnMsgbusWarpSink.this);
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
	
	public void sinkReport(SinkReportMessage sinkRepMsg) {
		try {
			Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/sink_report"),"POST", JSON.toJSON(sinkRepMsg).getBytes());
		} catch (WarpException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws WarpException {
		MdnMsgbusWarpSink sink = new MdnMsgbusWarpSink();
		sink.config();
		sink.init();
	}

}
