package edu.cmu.mdnsim.integratedtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.CreateNodeRequest;
import edu.cmu.mdnsim.nodes.NodeType;

/**
 * 
 * This test case is to test the WorkConfig input. A fake WorkConfig is generated
 * and sent to the master. The master should generate two nodes, one(sink) in NodeContainer
 * with label tomato, the other(source) in orange. This WorkConfig is with streamID
 * test-1. This test case simulates the simplest topology (source -> sink) in the simulator.
 * 
 * @author Jeremy Fu, Vinay Kumar Vavili, Jigar Patel, Hao Wang
 *
 */
public class WorkConfigTestCase implements MessageBusTestCase {
	
	private MessageBusClient msgBusClient;
	private String simuID;
	
	public WorkConfigTestCase(MessageBusClient client, String simuID) {
		msgBusClient = client;
		this.simuID = simuID;
	}
	
	@Override
	public void execute() {
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuID);
		List<StreamSpec> streamSpecList = new ArrayList<StreamSpec>();
		StreamSpec streamSpec = new StreamSpec();
		streamSpec.StreamId = simuID;
		streamSpec.ByteRate = "625000";
		streamSpec.DataSize = "20000000";
		ArrayList<HashMap<String, String>> flow = new ArrayList<HashMap<String, String>>();
		
		HashMap<String, String> sinkInfo = new HashMap<String, String>();
		sinkInfo.put("NodeType","SINK");
		sinkInfo.put("NodeId", "tomato:sink1");
		sinkInfo.put("UpstreamId", "orange:proc1");
		flow.add(sinkInfo);
		
		HashMap<String, String> procInfo = new HashMap<String, String>();
		procInfo.put("NodeType", "PROC");
		procInfo.put("NodeId", "orange:proc1");
		procInfo.put("UpstreamId", "orange:source1");
		procInfo.put("ProcessingLoop", "3000");
		procInfo.put("ProcessingMemory", "1000");
		flow.add(procInfo);
		
		HashMap<String, String> sourceInfo = new HashMap<String, String>();
		sourceInfo.put("NodeType", "SOURCE");
		sourceInfo.put("NodeId", "orange:source1");
		sourceInfo.put("UpstreamId", "NULL");
		flow.add(sourceInfo);
		
		streamSpec.Flow = flow;
		streamSpecList.add(streamSpec);
		wc.setStreamSpecList(streamSpecList);

		try {
			msgBusClient.sendToMaster("/", "/validate_user_spec", "POST", wc);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	
}

	
