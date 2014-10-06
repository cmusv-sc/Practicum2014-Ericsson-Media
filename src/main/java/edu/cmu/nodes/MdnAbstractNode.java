package edu.cmu.nodes;

import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public abstract class MdnAbstractNode implements MdnNodeInterface {

	// 1 kilo byte
	public static final int STD_DATAGRAM_SIZE = 1000;
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
	
	public String currentTime(){
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		Date date = new Date();
		return dateFormat.format(date);
	}

}
