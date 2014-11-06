package edu.cmu.config;

import static org.junit.Assert.*;

import org.junit.Test;

import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.config.ScriptReader;
import edu.cmu.mdnsim.config.WorkConfig;

public class ScriptReaderTest {

	@Test
	public void testReadScript() {
		ScriptReader scriptReader = new ScriptReader();
		WorkConfig workConfig = scriptReader.getWorkConfigFromScript("script");
				
		assertEquals("100", workConfig.getStreamSpecList().get(0).ByteRate);
		assertEquals("us-west-3", workConfig.getStreamSpecList().get(1).Flow.get(0).get("upstreamId"));
		
		
	}

}
