package edu.cmu.mdnsim.integratedtest;

import java.util.HashMap;

import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;

public class WorkConfigFactory {
	
	public static WorkConfig getWorkConfig(Scenario scenario, String... streamIDs) {
		
		if (scenario == Scenario.SINGLE_STREAM_SINGLE_FLOW && streamIDs.length == 1) {
			return singleStreamSingleFlow(streamIDs[0]);
		}
		if (scenario == Scenario.TWO_STREMS_TWO_FLOWS && streamIDs.length == 2) {
			return twoStreamTwoFlow(streamIDs[0], streamIDs[1]);
		} else {
			return null;
		}
		
		
	}
	
	private static WorkConfig twoStreamTwoFlow(String streamID1, String streamID2) {
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(streamID1);
		
		/* Stream 1 */
		Stream stream1 = new Stream();
		stream1.setStreamId(streamID1);
		stream1.setKiloBitRate("625000");
		stream1.setDataSize("20000000");
		
		Flow flow1 = new Flow();
		
		HashMap<String, String> sinkInfo1 = new HashMap<String, String>();
		sinkInfo1.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sinkInfo1.put(Flow.NODE_ID, "tomato:sink1");
		sinkInfo1.put(Flow.UPSTREAM_ID, "apple:proc1");
		flow1.addNode(sinkInfo1);
		
		HashMap<String, String> procInfo1 = new HashMap<String, String>();
		procInfo1.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		procInfo1.put(Flow.NODE_ID, "apple:proc1");
		procInfo1.put(Flow.UPSTREAM_ID, "orange:source1");
		procInfo1.put("ProcessingLoop", "3000");
		procInfo1.put("ProcessingMemory", "1000");
		flow1.addNode(procInfo1);
		
		HashMap<String, String> sourceInfo1 = new HashMap<String, String>();
		sourceInfo1.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		sourceInfo1.put(Flow.NODE_ID, "orange:source1");
		sourceInfo1.put(Flow.UPSTREAM_ID, "NULL");
		flow1.addNode(sourceInfo1);

		stream1.addFlow(flow1);
		wc.addStream(stream1);
		
		/* Stream 2 */
		Stream stream2 = new Stream();
		stream2.setStreamId(streamID2);
		stream2.setKiloBitRate("625000");
		stream2.setDataSize("20000000");
		
		Flow flow2 = new Flow();
		
		HashMap<String, String> sinkInfo2 = new HashMap<String, String>();
		sinkInfo2.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sinkInfo2.put(Flow.NODE_ID, "apple:sink2");
		sinkInfo2.put(Flow.UPSTREAM_ID, "orange:proc1");
		flow2.addNode(sinkInfo2);
		
		HashMap<String, String> procInfo2 = new HashMap<String, String>();
		procInfo2.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		procInfo2.put(Flow.NODE_ID, "orange:proc1");
		procInfo2.put(Flow.UPSTREAM_ID, "tomato:source1");
		procInfo2.put("ProcessingLoop", "3000");
		procInfo2.put("ProcessingMemory", "1000");
		flow2.addNode(procInfo2);
		
		HashMap<String, String> sourceInfo2 = new HashMap<String, String>();
		sourceInfo2.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		sourceInfo2.put(Flow.NODE_ID, "tomato:source1");
		sourceInfo2.put(Flow.UPSTREAM_ID, "NULL");
		flow2.addNode(sourceInfo2);

		stream2.addFlow(flow2);
		wc.addStream(stream2);
		
		return wc;
	}
	
	private static WorkConfig singleStreamSingleFlow(String streamID) {
		WorkConfig wc = new WorkConfig();
		wc.setSimId(streamID);
		
		/* Stream 1 */
		Stream stream1 = new Stream();
		stream1.setStreamId(streamID);
		stream1.setKiloBitRate("625000");
		stream1.setDataSize("20000000");
		
		Flow flow1 = new Flow();
		
		HashMap<String, String> sinkInfo1 = new HashMap<String, String>();
		sinkInfo1.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sinkInfo1.put(Flow.NODE_ID, "tomato:sink1");
		sinkInfo1.put(Flow.UPSTREAM_ID, "apple:proc1");
		flow1.addNode(sinkInfo1);
		
		HashMap<String, String> procInfo1 = new HashMap<String, String>();
		procInfo1.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		procInfo1.put(Flow.NODE_ID, "apple:proc1");
		procInfo1.put(Flow.UPSTREAM_ID, "orange:source1");
		procInfo1.put("ProcessingLoop", "3000");
		procInfo1.put("ProcessingMemory", "1000");
		flow1.addNode(procInfo1);
		
		HashMap<String, String> sourceInfo1 = new HashMap<String, String>();
		sourceInfo1.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		sourceInfo1.put(Flow.NODE_ID, "orange:source1");
		sourceInfo1.put(Flow.UPSTREAM_ID, "NULL");
		flow1.addNode(sourceInfo1);

		stream1.addFlow(flow1);
		wc.addStream(stream1);
		return wc;
	}
	
	public static enum Scenario {
		SINGLE_STREAM_SINGLE_FLOW, TWO_STREMS_TWO_FLOWS;
	}
	
}
