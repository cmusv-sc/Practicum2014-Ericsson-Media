package edu.cmu.mdnsim.nodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Utility {
	
	/**
	 * It converts the IPv4 address in dotted decimal representation to the byte
	 * array.
	 * 
	 * @param ipv4Address
	 * @return
	 */
	static byte[] convertIPv4StrToByteArray(String ipv4Address) {

		if(!Pattern.matches("\\d+\\.\\d+\\.\\d+\\.\\d+", ipv4Address)) {
			throw new IllegalArgumentException (String.format("The %s is not IPv4 address in dotted decimal representation.", ipv4Address));
		}
		String[] segs = ipv4Address.split("\\.");
		byte[] ret = new byte[4];
		
		for (int i = 0; i < 4; i++) {
			if ((Integer.parseInt(segs[i]) & (0xFFFFFF00)) != 0) {
				throw new IllegalArgumentException (String.format("The part %s in %s is out of value boundary 0 - 255", segs[i], ipv4Address));
			}
			ret[i] = (byte)Integer.parseInt(segs[i]);
		}
		
		return ret;
	}
}
