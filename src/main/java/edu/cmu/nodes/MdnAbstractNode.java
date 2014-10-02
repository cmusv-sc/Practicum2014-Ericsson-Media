package edu.cmu.nodes;

import java.net.UnknownHostException;

public abstract class MdnAbstractNode implements MdnNodeInterface {

	public static final int STD_DATAGRAM_SIZE = 1024;
	String hostAddr;
	
	public MdnAbstractNode() {
		setHostAddr();
	}

	@Override
	public String getHostAddr() {
		return hostAddr;
	}

	private void setHostAddr() {
		// Note: This may not be sufficient for proper DNS resolution
		// Refer http://stackoverflow.com/questions/7348711/recommended-way-to-get-hostname-in-java?lq=1
		try {
			hostAddr = java.net.InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException uhe) {
			uhe.printStackTrace();
		}
	}

}
