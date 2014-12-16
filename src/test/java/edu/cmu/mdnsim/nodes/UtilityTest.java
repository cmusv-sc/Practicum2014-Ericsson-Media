package edu.cmu.mdnsim.nodes;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * 
 * @author Geng Fu
 *
 */
public class UtilityTest {

	 @Rule
	 public ExpectedException exception = ExpectedException.none();
	 
	/**
	 * Test whether the edu.cmu.mdnsim.nodes.Utility.convertIPv4StrToByteArray()
	 * can convert the IP address string denoted in dotted decimal form to byte
	 * array.
	 */
	@Test
	public final void testConvertIPv4StrToByteArray() {
		
		byte[] answer = new byte[4];
		answer[0] = (byte)192;
		answer[1] = (byte)168;
		answer[2] = (byte)0;
		answer[3] = (byte)2;
		
		assertTrue("convert 192.168.0.2", Arrays.equals(answer, Utility.convertIPv4StrToByteArray("192.168.0.2")));
	
		exception.expect(IllegalArgumentException.class);
		exception.expectMessage("The 192.168.0.2.3 is not IPv4 address in dotted decimal representation");
		Utility.convertIPv4StrToByteArray("192.168.0.2.3");
		
		exception.expectMessage("The part 256 in 256.168.0.2 is out of value boundary 0 - 255");
		Utility.convertIPv4StrToByteArray("256.168.0.2");
	}
	


}

