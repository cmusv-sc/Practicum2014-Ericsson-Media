package edu.cmu.mdnsim.reporting;

import edu.cmu.mdnsim.nodes.NodePacket;

/**
 * 
 * 
 * 
 * @author JeremyFu
 *
 */
public class PacketLatencyTracker {
	
	private long avrEnd2EndLatency = Long.MIN_VALUE;
	
	private long avrLnk2LnkLatency = Long.MIN_VALUE;
	
	public synchronized void newPacket(NodePacket packet) {
		
		if (avrEnd2EndLatency == Long.MIN_VALUE) {
			avrEnd2EndLatency = SystemClock.currentTimeMillis() - packet.getTransmitTime();
		} else {
			avrEnd2EndLatency = (avrEnd2EndLatency + (SystemClock.currentTimeMillis() - packet.getTransmitTime())) / 2;
		}
		
		if (avrLnk2LnkLatency == Long.MIN_VALUE) {
			avrLnk2LnkLatency = SystemClock.currentTimeMillis() - packet.getForwardTime();
		} else {
			avrLnk2LnkLatency = (avrLnk2LnkLatency + (SystemClock.currentTimeMillis() - packet.getForwardTime())) / 2;
		}
		
//		System.out.println("[PacketLatencyTracker].newPacket(): packet.transmitTime(): " + packet.getTransmitTime() + "\tpacket.forwardTime(): " + packet.getForwardTime() + "avrEnd2EndLatency: " + this.avrEnd2EndLatency + "\tavrLnk2LnkLatency: " + this.avrLnk2LnkLatency);
		
		
	}
	
	public synchronized long getAvrEnd2EndLatency() {
		return this.avrEnd2EndLatency;
	}
	
	public synchronized long getAvrLnk2LnkLatency() {
		return this.avrLnk2LnkLatency;
	}
		
	

}
