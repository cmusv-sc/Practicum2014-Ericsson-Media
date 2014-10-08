package edu.cmu.messagebus.test;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.MdnMsgbusWarpNode;
import edu.cmu.messagebus.NodeType;
import edu.cmu.messagebus.message.StartSimulationRequest;

public class CopyOfStimulus extends MdnMsgbusWarpNode {
	
	
	public CopyOfStimulus() {
		super(NodeType.VOID);
	}

	public static void main(String[] args) throws WarpException, InterruptedException {
		
		CopyOfStimulus stimulus = new CopyOfStimulus();

		stimulus.config();
		stimulus.init();
		
		Thread.sleep(1000 * 5);
		StartSimulationRequest request = new StartSimulationRequest();
		request.setDataSize(4096);
		request.setStreamID("stream-1234");
		request.setStreamRate(1);
		request.setSourceNodeName("source-1");
		request.setSinkNodeName("sink-1");
		Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/start_simulation"), "POST", JSON.toJSON(request).getBytes());
	}
}
