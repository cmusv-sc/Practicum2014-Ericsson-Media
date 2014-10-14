package edu.cmu.config;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import com.ericsson.research.warp.util.JSON;

public class StreamConfigTest {

	@Test
	public void testSetFlow() {
		
		List<NodeConfig> list = new LinkedList<NodeConfig>();
		NodeConfig.Builder builder1 = new NodeConfig.Builder("Source");
		NodeConfig nodeConfig1 = builder1.bitrate(500).id("us-west-1").build();
		
		NodeConfig.Builder builder2 = new NodeConfig.Builder("Sink");
		NodeConfig nodeConfig2 = builder2.bitrate(400).build();
		list.add(nodeConfig1);
		list.add(nodeConfig2);
		
		StreamConfig streamConfig = new StreamConfig();
		streamConfig.setSize(10);
		streamConfig.setId("1");
		streamConfig.setFlow(list);
		
		System.out.println(JSON.toJSON(streamConfig));
		
		assertEquals(500, streamConfig.getFlow().get(0).getBitrate());
		assertEquals(400, streamConfig.getFlow().get(1).getBitrate());
		assertEquals(10, streamConfig.getSize());
		assertEquals("1", streamConfig.getId());
		assertEquals("us-west-1", streamConfig.getFlow().get(0).getId());
	}

}
