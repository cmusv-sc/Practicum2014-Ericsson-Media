package edu.cmu.config;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.cmu.mdnsim.config.NodeConfig;

public class NodeConfigTest extends NodeConfig {

	@Test
	public void testNodeConfigBuilder() {
		NodeConfig.Builder builder = new NodeConfig.Builder("Source");
		NodeConfig nodeConfig = builder.id("us-west-1").bitrate(500).label("us-west").desIP("localhost").desPort(8080).upStreamNodeId("none").build();
		
		assertEquals(500, nodeConfig.getBitrate());
		assertEquals("Source", nodeConfig.getNodeType());
		assertEquals("us-west", nodeConfig.getLabel());
		assertEquals("localhost", nodeConfig.getDesIP());
		assertEquals(8080, nodeConfig.getDesPort());
		assertEquals("us-west-1", nodeConfig.getId());
		assertEquals("none", nodeConfig.getUpStreamNodeId());
	}
}
