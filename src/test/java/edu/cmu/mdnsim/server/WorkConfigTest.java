package edu.cmu.mdnsim.server;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;

public class WorkConfigTest {
	
	@org.junit.Test
	public void testIsValidWorkConfig() {
		
		String simuID = "test";
		
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
		sinkInfo.put("UpstreamId", "apple:relay1");
		flow.add(sinkInfo);
		
		HashMap<String, String> relayInfo = new HashMap<String, String>();
		relayInfo.put("NodeType", "Relay");
		relayInfo.put("NodeId", "apple:relay1");
		relayInfo.put("UpstreamId", "orange:source1");
		relayInfo.put("DownstreamId", "tomato:sink1");
		flow.add(relayInfo);
		
		HashMap<String, String> sourceInfo = new HashMap<String, String>();
		sourceInfo.put("NodeType", "SOURCE");
		sourceInfo.put("NodeId", "orange:source1");
		sourceInfo.put("DownstreamId", "apple:relay1");
		flow.add(sourceInfo);
		
		streamSpec.Flow = flow;
		streamSpecList.add(streamSpec);
		wc.setStreamSpecList(streamSpecList);
		
		for (StreamSpec s : wc.getStreamSpecList()) {
			assertTrue("Test valid WorkConfig", Master.isValidWorkConfig(s));
		}
		
	}
	
}
