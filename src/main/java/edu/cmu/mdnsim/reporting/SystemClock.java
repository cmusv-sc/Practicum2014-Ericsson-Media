package edu.cmu.mdnsim.reporting;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class SystemClock {
	
	private static final String NTPServerAddress = "clock.psu.edu";
	
	private static long offset = 0;
	
	private static boolean hasOffsetBeenSet = false;
	
	private SystemClock() {
		
	}
	
	
	static {
		Long offsetC = 0L;
		int counter = 0;
		for (int i = 0; i < 10; i++) { 
			try {
				offsetC += synchronize();
				counter++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (counter != 0) {
			updateOffsets(offsetC / counter);
		} else {
			updateOffsets(0);
		}
	}
	
	
	private static Long synchronize() throws IOException, SocketException{
		
		NTPUDPClient client = new NTPUDPClient();
		
		client.setDefaultTimeout(10000);
		
		client.open();
		
		InetAddress hostAddr = InetAddress.getByName(NTPServerAddress);
		
		TimeInfo info = client.getTime(hostAddr);
		
		info.computeDetails();
		
		return info.getOffset();

           
	}
	
	private static synchronized long getOffset() {
		return offset;
	}
	
	private static synchronized void updateOffsets(long newOffset) {
		
//		if (!hasOffsetBeenSet) {
//			offset = newOffset;
//			hasOffsetBeenSet = true;
//		} else {
//			offset = (offset + newOffset) / 2;
//		}
		
		offset = newOffset;
		System.out.println("Set offset to: " + offset);
	}
	
	public static long currentTimeMillis() {
		return System.currentTimeMillis() + getOffset();
	}
}
