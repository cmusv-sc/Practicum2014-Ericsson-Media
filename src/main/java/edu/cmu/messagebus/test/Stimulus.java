package edu.cmu.messagebus.test;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.MDNNode;
import edu.cmu.messagebus.NodeType;
import edu.cmu.messagebus.message.StartSimulationRequest;

public class Stimulus extends MDNNode {
	
	
	public Stimulus() {
		super(NodeType.VOID);
	}

	public static void main(String[] args) throws WarpException, InterruptedException {
		
		Stimulus stimulus = new Stimulus();

		stimulus.config();
		stimulus.init();
		
		Thread.sleep(1000 * 5);
		StartSimulationRequest request = new StartSimulationRequest();
		request.setDataSize(1000);
		request.setStreamID("stream-123");
		request.setStreamRate(1);
		request.setSourceNodeName("source-1");
		request.setSinkNodeName("sink-2");
		Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/start_simulation"), "POST", JSON.toJSON(request).getBytes());
	}
}
