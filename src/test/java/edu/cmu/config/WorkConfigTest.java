package edu.cmu.config;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.NodeConfig;
import edu.cmu.mdnsim.config.StreamConfig;
import edu.cmu.mdnsim.config.WorkConfig;

public class WorkConfigTest {

	@Test
	public void test() {
		
		/*
		 * Stream1
		 */
		NodeConfig.Builder builder1 = new NodeConfig.Builder("Source");
		NodeConfig nodeConfig1 = builder1.bitrate(500).id("us-west-1").build();

		List<NodeConfig> list1 = new LinkedList<NodeConfig>();
		list1.add(nodeConfig1);
		
		StreamConfig streamConfig1 = new StreamConfig();
		streamConfig1.setSize(10);
		streamConfig1.setId("1");
		streamConfig1.setFlow(list1);
		
		/*
		 * Stream2
		 */
		NodeConfig.Builder builder2 = new NodeConfig.Builder("Sink");
		NodeConfig nodeConfig2 = builder2.bitrate(400).build();

		List<NodeConfig> list2 = new LinkedList<NodeConfig>();
		list2.add(nodeConfig2);
		
		StreamConfig streamConfig2 = new StreamConfig();
		streamConfig2.setSize(20);
		streamConfig2.setId("2");
		streamConfig2.setFlow(list2);
		
		/*
		 * WorkConfig
		 */	
//		WorkConfig workConfig = new WorkConfig();
//		workConfig.addStreamConfig(streamConfig1);
//		workConfig.addStreamConfig(streamConfig2);
//		
//		System.out.println(JSON.toJSON(workConfig));
//		
//		assertEquals(500, workConfig.getStreamConfigList().get(0).getFlow().get(0).getBitrate());
//		assertEquals(10, workConfig.getStreamConfigList().get(0).getSize());
//		assertEquals(400,workConfig.getStreamConfigList().get(1).getFlow().get(0).getBitrate());
	}

}
