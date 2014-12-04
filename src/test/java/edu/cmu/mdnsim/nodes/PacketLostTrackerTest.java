package edu.cmu.mdnsim.nodes;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class PacketLostTrackerTest {

	@Before
	public void setUp() throws Exception {
	}

	//public PacketLostTracker(int totalData, int rate, int packetLength, int timeout)
	@Test
	public final void testUpdatePacketLost() {
		int totalData = 10000;
		int rate = 1000;
		int packetLength = 1000;
		int timeout = 5 * 1000;
		PacketLostTracker packetLostTracker = new PacketLostTracker(totalData, rate, packetLength, timeout, 0);
		
		for(int i = 0; i < 10; i++){
			packetLostTracker.updatePacketLost(i);
		}
		assertEquals("all 10 packets are received",packetLostTracker.getLostPacketNum(), 0);
		
		packetLostTracker = new PacketLostTracker(totalData, rate, packetLength, timeout, 0);
		for(int i = 0; i < 9; i++){
			packetLostTracker.updatePacketLost(i);
		}
		packetLostTracker.updatePacketLostForLastTime();
		assertEquals("1 packet is lost at the end",packetLostTracker.getLostPacketNum(), 1);


		packetLostTracker = new PacketLostTracker(totalData, rate, packetLength, timeout, 0);
		for(int i = 0; i < 10; i++){
			if(i == 6){
				continue;
			}
			packetLostTracker.updatePacketLost(i);
		}
		packetLostTracker.updatePacketLostForLastTime();
		assertEquals("1 packet is lost in the middle (7th)",packetLostTracker.getLostPacketNum(), 1);
	}
	

	@Test
	public final void testUpdatePacketLostForTimeout() {
		int totalData = 10000;
		int rate = 1000;
		int packetLength = 1000;
		int timeout = 5 * 1000;
		PacketLostTracker packetLostTracker = new PacketLostTracker(totalData, rate, packetLength, timeout, 0);
		
		packetLostTracker.updatePacketLostForLastTime();
		assertEquals("10 packets are lost", packetLostTracker.getLostPacketNum(), 10);
	}

}
