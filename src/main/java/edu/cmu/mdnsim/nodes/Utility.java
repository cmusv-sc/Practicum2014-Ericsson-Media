package edu.cmu.mdnsim.nodes;

class Utility {
	
	static byte[] convertIPv4StrToByteArray(String ipv4Address) {

		String[] segs = ipv4Address.split("\\.");
		if (segs.length != 4) {
			throw new RuntimeException (String.format("The %s is not IPv4 address in dotted decimal representation. seg amount = %d", ipv4Address, segs.length));
		}
		
		byte[] ret = new byte[4];
		
		for (int i = 0; i < 4; i++) {
			if ((Integer.parseInt(segs[i]) & (0xFFFFFF00)) != 0) {
				throw new RuntimeException (String.format("The part %s in %s is out of value boundary 0 - 255", segs[i], ipv4Address));
			}
			ret[i] = (byte)Integer.parseInt(segs[i]);
		}
		
		return ret;
	}
}
