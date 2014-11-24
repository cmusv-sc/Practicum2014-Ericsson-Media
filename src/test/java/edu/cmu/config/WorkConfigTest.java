package edu.cmu.config;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;

public class WorkConfigTest {
	
	@org.junit.Test
	public void testIsValidWorkConfig() {
		
		String simuID = "test";
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuID);

		Stream stream = new Stream(simuID, "20000000", "20");
		
		Flow flow = new Flow();
		Map<String, String> sink = new HashMap<String, String>();
		sink.put(Flow.NODE_TYPE,"SINK");
		sink.put(Flow.NODE_ID, "tomato:sink1");
		sink.put(Flow.UPSTREAM_ID, "apple:relay1");
		flow.addNode(sink);
		
		Map<String, String> relay = new HashMap<String, String>();
		relay.put(Flow.NODE_TYPE, "RELAY");
		relay.put(Flow.NODE_ID, "apple:relay1");
		relay.put(Flow.UPSTREAM_ID, "orange:source1");
		relay.put(Flow.DOWNSTREAM_ID, "tomato:sink1");
		flow.addNode(relay);
		
		HashMap<String, String> source = new HashMap<String, String>();
		source.put(Flow.NODE_TYPE, "SOURCE");
		source.put(Flow.NODE_ID, "orange:source1");
		source.put(Flow.DOWNSTREAM_ID, "apple:relay1");
		flow.addNode(source);
		
		stream.addFlow(flow);
		wc.addStream(stream);
		
		for (Stream s : wc.getStreamList()) {
			assertTrue("Test valid WorkConfig", wc.isValidWorkConfig());
		}
		
	}
	
}
