package edu.cmu.mdnsim.reporting;

import static org.junit.Assert.assertEquals;

import java.util.Random;

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

	
	@Test//(timeout=1000)
	public final void testInorderDelivery() {
		
		PacketLostTracker packetLostTracker = new PacketLostTracker(10);
		
		for(int i = 0; i < 10; i++){
			packetLostTracker.updatePacketLost(i);
		}
		assertEquals("all 10 packets are received",packetLostTracker.getLostPacketNum(), 0);
		
		packetLostTracker.reset();
		for (int i = 0; i < 20; i++) {
			packetLostTracker.updatePacketLost(i);
		}
		assertEquals("all 20 packets are received",packetLostTracker.getLostPacketNum(), 0);
		
		packetLostTracker.reset();
		for (int i = 0; i < 25; i++) {
			packetLostTracker.updatePacketLost(i);
		}
		assertEquals("all 25 packets are received",packetLostTracker.getLostPacketNum(), 0);
		
		packetLostTracker.reset();
		for (int i = 0; i < 100; i++) {
			if (i / 10 != i % 10) {
				packetLostTracker.updatePacketLost(i);
			}
		}
		
		assertEquals("9 out 100 packets are lost",packetLostTracker.getLostPacketNum(), 9);
		
	}
	
	@Test
	public void testOutOfOrderDeliveryWithoutPacketLoss() {
		PacketLostTracker packetLostTracker = new PacketLostTracker(10);
		/*
		 * Test out-of-order delivery without packet loss in timeout
		 */
		packetLostTracker.updatePacketLost(2);
		packetLostTracker.updatePacketLost(4);
		packetLostTracker.updatePacketLost(7);
		packetLostTracker.updatePacketLost(9);
		packetLostTracker.updatePacketLost(0);
		packetLostTracker.updatePacketLost(1);
		packetLostTracker.updatePacketLost(3);
		packetLostTracker.updatePacketLost(5);
		packetLostTracker.updatePacketLost(6);
		packetLostTracker.updatePacketLost(8);
		assertEquals("all 10 packets are received",packetLostTracker.getLostPacketNum(), 0);
		
		packetLostTracker.reset();
		packetLostTracker.updatePacketLost(2);
		System.out.println(packetLostTracker);
		packetLostTracker.updatePacketLost(4);
		packetLostTracker.updatePacketLost(7);
		packetLostTracker.updatePacketLost(9);
		packetLostTracker.updatePacketLost(0);
		packetLostTracker.updatePacketLost(1);
		packetLostTracker.updatePacketLost(5);
		packetLostTracker.updatePacketLost(6);
		packetLostTracker.updatePacketLost(14);
		assertEquals("3, 8, 10, 11, 12, 13 out of 0-14 lost",packetLostTracker.getLostPacketNum(), 6);
		
		
		packetLostTracker.updatePacketLost(13);
		assertEquals("3, 8, 10, 11, 12 out of 0-14 lost",packetLostTracker.getLostPacketNum(), 5);
		packetLostTracker.updatePacketLost(12);
		assertEquals("3, 8, 10, 11, 12 out of 0-14 lost",packetLostTracker.getLostPacketNum(), 4);
		packetLostTracker.updatePacketLost(3);
		assertEquals("3, 8, 10, 11, 12 out of 0-14 lost",packetLostTracker.getLostPacketNum(), 4);
		packetLostTracker.updatePacketLost(8);
		assertEquals("3, 8, 10, 11, 12 out of 0-14 lost",packetLostTracker.getLostPacketNum(), 3);
		
		packetLostTracker.reset();
		packetLostTracker.updatePacketLost(1);
		packetLostTracker.updatePacketLost(5);
		assertEquals("0, 2, 3, 4 out of 5 lost",packetLostTracker.getLostPacketNum(), 4);
		packetLostTracker.updatePacketLost(40);
		assertEquals("0, 2, 3, 4, 6 - 19 out of 0 - 30 lost",packetLostTracker.getLostPacketNum(), 38);
	}
	
	@Test
	public void testFivePercentLoss() {
		int lost = 0;
		PacketLostTracker packetLostTracker = new PacketLostTracker(10);
		for (int i=0; i< 10000; i++)
		{
			if (Math.random() < 0.05)
				lost++;
			else
				packetLostTracker.updatePacketLost(i);
		}

		System.out.println(lost);
		System.out.println(packetLostTracker.getLostPacketNum());
	}

}
