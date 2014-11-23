package edu.cmu.mdnsim.integratedtest;

import java.util.HashMap;
import java.util.Map;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.config.Stream;
import edu.cmu.mdnsim.config.WorkConfig;

public class WorkConfigFactory {
	
	public static WorkConfig getWorkConfig(String simuID, Scenario scenario, String... streamIDs) {
		
		if (scenario == Scenario.SINGLE_STREAM_SINGLE_FLOW && streamIDs.length == 1) {
			return singleStreamSingleFlow(simuID, streamIDs[0]);
		}
		if (scenario == Scenario.TWO_STREMS_TWO_FLOWS && streamIDs.length == 2) {
			return twoStreamTwoFlow(simuID, streamIDs[0], streamIDs[1]);
		}
		if (scenario == Scenario.ONE_STREAM_TWO_FLOWS && streamIDs.length == 1) {
			return singleStreamTwoFlows(simuID, streamIDs[0]);
		}
		
		return null;
		
	}
	
	private static WorkConfig twoStreamTwoFlow(String simuID, String streamID1, String streamID2) {
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuID);
		
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
		procInfo1.put(Flow.PROCESSING_LOOP, "3000");
		procInfo1.put(Flow.PROCESSING_MEMORY, "1000");
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
		procInfo2.put(Flow.PROCESSING_LOOP, "3000");
		procInfo2.put(Flow.PROCESSING_MEMORY, "1000");
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
	
	private static WorkConfig singleStreamSingleFlow(String simuID, String streamID) {
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuID);
		
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
		procInfo1.put(Flow.PROCESSING_LOOP, "3000");
		procInfo1.put(Flow.PROCESSING_MEMORY, "1000");
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
	
	private static WorkConfig singleStreamTwoFlows (String simuId, String streamId) {
		
		final String SINK1_ID = "tomato:sink1";
		final String SINK2_ID = "banana:sink2";
		final String RELAY_ID = "orange:relay1";
		final String PROC_ID  = "apple:processing1";
		final String SRC_ID = "lemon:source1";
		
		WorkConfig wc = new WorkConfig();
		wc.setSimId(simuId);
		
		/* Create a stream */
		Stream stream = new Stream();
		stream.setStreamId(streamId);
		stream.setKiloBitRate("625000");
		stream.setDataSize("20000000");
		
		/* Add one flow */
		Flow flow1 = new Flow();
		Map<String, String> sink1 = new HashMap<String, String>();
		sink1.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sink1.put(Flow.NODE_ID, SINK1_ID);
		sink1.put(Flow.UPSTREAM_ID, RELAY_ID);
		
		flow1.addNode(sink1);
		
		Map<String, String> relay = new HashMap<String, String>();
		relay.put(Flow.NODE_TYPE, WorkConfig.RELAY_NODE_TYPE_INPUT);
		relay.put(Flow.NODE_ID, RELAY_ID);
		relay.put(Flow.UPSTREAM_ID, PROC_ID);
		
		flow1.addNode(relay);
		
		Map<String, String> proc = new HashMap<String, String>();
		proc.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		proc.put(Flow.NODE_ID, PROC_ID);
		proc.put(Flow.UPSTREAM_ID, SRC_ID);
		proc.put(Flow.PROCESSING_LOOP, "3000");
		proc.put(Flow.PROCESSING_MEMORY, "1000");
		
		flow1.addNode(proc);
		
		Map<String, String> src = new HashMap<String, String>();
		src.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		src.put(Flow.NODE_ID, SRC_ID);
		src.put(Flow.UPSTREAM_ID, "NULL");
		
		flow1.addNode(src);
		
		stream.addFlow(flow1);
		
		/* Create 2nd stream */
		Flow flow2 = new Flow();
		Map<String, String> sink2 = new HashMap<String, String>();
		sink2.put(Flow.NODE_TYPE, WorkConfig.SINK_NODE_TYPE_INPUT);
		sink2.put(Flow.NODE_ID, SINK2_ID);
		sink2.put(Flow.UPSTREAM_ID, RELAY_ID);
		
		flow2.addNode(sink1);
		
		relay = new HashMap<String, String>();
		relay.put(Flow.NODE_TYPE, WorkConfig.RELAY_NODE_TYPE_INPUT);
		relay.put(Flow.NODE_ID, RELAY_ID);
		relay.put(Flow.UPSTREAM_ID, PROC_ID);
		
		flow2.addNode(relay);
		
		proc = new HashMap<String, String>();
		proc.put(Flow.NODE_TYPE, WorkConfig.PROC_NODE_TYPE_INPUT);
		proc.put(Flow.NODE_ID, PROC_ID);
		proc.put(Flow.UPSTREAM_ID, SRC_ID);
		proc.put(Flow.PROCESSING_LOOP, "3000");
		proc.put(Flow.PROCESSING_MEMORY, "1000");
		
		flow2.addNode(proc);
		
		src = new HashMap<String, String>();
		src.put(Flow.NODE_TYPE, WorkConfig.SOURCE_NODE_TYPE_INPUT);
		src.put(Flow.NODE_ID, SRC_ID);
		src.put(Flow.UPSTREAM_ID, "NULL");
		
		flow2.addNode(src);
		
		stream.addFlow(flow2);
		wc.addStream(stream);
		return wc;
	}
	
	
	public static enum Scenario {
		SINGLE_STREAM_SINGLE_FLOW, TWO_STREMS_TWO_FLOWS, ONE_STREAM_TWO_FLOWS;
	}
	
}
