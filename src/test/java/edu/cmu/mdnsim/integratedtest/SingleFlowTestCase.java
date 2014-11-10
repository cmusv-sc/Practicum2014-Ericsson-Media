package edu.cmu.mdnsim.integratedtest;

import java.util.HashMap;

import com.ericsson.research.warp.util.JSON;

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
		sinkInfo.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sinkInfo.put(Flow.NODE_ID, "tomato:sink1");
		sinkInfo.put(Flow.UPSTREAM_ID, "apple:proc1");
		flow.addNode(sinkInfo);
		
		HashMap<String, String> procInfo = new HashMap<String, String>();
		procInfo.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		procInfo.put(Flow.NODE_ID, "apple:proc1");
		procInfo.put(Flow.UPSTREAM_ID, "orange:source1");
		procInfo.put("ProcessingLoop", "3000");
		procInfo.put("ProcessingMemory", "1000");
		flow.addNode(procInfo);
		
		HashMap<String, String> sourceInfo = new HashMap<String, String>();
		sourceInfo.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		sourceInfo.put(Flow.NODE_ID, "orange:source1");
		sourceInfo.put(Flow.UPSTREAM_ID, "NULL");
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

	
