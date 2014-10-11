package edu.cmu.messagebus.test;

import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.exception.MessageBusException;
import edu.cmu.messagebus.message.StartSimulationRequest;
import edu.cmu.nodes.NodeContainer;

public class Stimulus extends NodeContainer {
	
	
	public Stimulus() throws MessageBusException {
		super();
	}

	public static void main(String[] args) throws WarpException {
		
		Stimulus stimulus = new Stimulus();

		stimulus.config();
		stimulus.init();
		
		try {
			Thread.sleep(1000 * 5);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StartSimulationRequest request = new StartSimulationRequest();
		request.setDataSize(2048);
		request.setStreamID("stream-123");
		request.setStreamRate(1);
		request.setSourceNodeName("node-0000");
		request.setSinkNodeName("node-0001");
		Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/start_simulation"), "POST", JSON.toJSON(request).getBytes());
	}
}
