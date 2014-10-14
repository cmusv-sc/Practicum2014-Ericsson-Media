package edu.cmu.config;

import static org.junit.Assert.*;

import org.junit.Test;

import com.ericsson.research.warp.util.JSON;

public class ScriptReaderTest {

	@Test
	public void testReadScript() {
		ScriptReader scriptReader = new ScriptReader();
		WorkConfig workConfig = scriptReader.getWorkConfigFromScript("script");
		
		//JSONDeserializer jsonDeserializer = new JSONDeserializer();
		//StreamConfig stream2 = (StreamConfig) jsonDeserializer.deserialize(new InputStreamReader(inputStream), StreamConfig.class);
		//System.out.println(JSON.toJSON(workConfig));
		
		assertEquals(100, workConfig.getStreamConfigList().get(0).getFlow().get(0).getBitrate());
		assertEquals("us-west-3", workConfig.getStreamConfigList().get(1).getFlow().get(1).getUpStreamNodeId());
		
		
	}

}
