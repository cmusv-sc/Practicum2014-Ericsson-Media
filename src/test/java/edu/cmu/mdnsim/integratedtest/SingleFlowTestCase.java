package edu.cmu.mdnsim.integratedtest;

import java.util.HashMap;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
 * This test case is to test the WorkConfig input. A fake WorkConfig is 
 * generated and sent to the master. The master should generate three nodes, 
 * one sink in NodeContainer with label tomato, one processing node in 
 * NodeContainer with label apple, the other(source) in orange. This WorkConfig 
 * is with streamID test-1. This test case simulates the simple one flow 
 * topology (source ->...-> sink) in the simulator.
 * 
 * @author Jeremy Fu
 * @author Vinay Kumar Vavili
 * @author Jigar Patel
 * @author Hao Wang
 *
 */
public class SingleFlowTestCase implements MessageBusTestCase {
	
	private MessageBusClient msgBusClient;
	private String simuID;
	
	public SingleFlowTestCase(MessageBusClient client, String simuID) {
		msgBusClient = client;
		this.simuID = simuID;
	}
	
	@Override
	public void execute() {
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuID);
		
		Stream stream = new Stream();
		stream.setStreamId(simuID);
		stream.setKiloBitRate("625000");
		stream.setDataSize("20000000");
		
		Flow flow = new Flow();
		
		HashMap<String, String> sinkInfo = new HashMap<String, String>();
		sinkInfo.put("NodeType","SINK");
		sinkInfo.put("NodeId", "tomato:sink1");
		sinkInfo.put("UpstreamId", "orange:source1");
		flow.addNode(sinkInfo);
		
		HashMap<String, String> procInfo = new HashMap<String, String>();
		procInfo.put("NodeType", "PROCESSING");
		procInfo.put("NodeId", "apple:proc1");
		procInfo.put("UpstreamId", "orange:source1");
		procInfo.put("ProcessingLoop", "3000");
		procInfo.put("ProcessingMemory", "1000");
		flow.addNode(procInfo);
		
		HashMap<String, String> sourceInfo = new HashMap<String, String>();
		sourceInfo.put("NodeType", "SOURCE");
		sourceInfo.put("NodeId", "orange:source1");
		sourceInfo.put("UpstreamId", "NULL");
		flow.addNode(sourceInfo);

		stream.addFlow(flow);
		wc.addStream(stream);

		try {
			msgBusClient.sendToMaster("/", "/work_config", "POST", wc);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	
}

	
