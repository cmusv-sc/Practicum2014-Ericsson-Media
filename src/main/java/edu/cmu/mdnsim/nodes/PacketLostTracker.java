package edu.cmu.mdnsim.nodes;

import java.util.concurrent.atomic.AtomicInteger;

public class PacketLostTracker {
	
	private AtomicInteger lostPacketNum = new AtomicInteger(0);
	
	private int expectedMaxPacketId;
	private int packetNumInAWindow;
	private int lowPacketIdBoundry;
	private int highPacketIdBoundry;
	private int receivedPacketNumInAWindow;
	

	public PacketLostTracker(int totalData, int rate, int packetLength, int timeout){
		expectedMaxPacketId = (int) Math.ceil(totalData * 1.0 / packetLength) - 1;
		double packetNumPerSecond = rate * 1.0 / packetLength;
		packetNumInAWindow = (int) Math.ceil(packetNumPerSecond * timeout / 1000);
		lowPacketIdBoundry = 0;
		highPacketIdBoundry = Math.min(packetNumInAWindow - 1, expectedMaxPacketId);
		receivedPacketNumInAWindow = 0;
	}

	public void updatePacketLost(int packetId){
		if(packetId > highPacketIdBoundry){
			setLostPacketNum(this.getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (packetId - highPacketIdBoundry - 1));
			lowPacketIdBoundry = packetId;
			highPacketIdBoundry = Math.min(lowPacketIdBoundry + packetNumInAWindow - 1, expectedMaxPacketId);
			receivedPacketNumInAWindow = 1;
		} else if(packetId >= lowPacketIdBoundry && packetId <= highPacketIdBoundry){
			receivedPacketNumInAWindow++;
		}
	}
	
	public void updatePacketLostForTimeout(){
		setLostPacketNum(getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (expectedMaxPacketId - highPacketIdBoundry));
	}
	
	
	public synchronized int getLostPacketNum() {
		return lostPacketNum.get();
	}

	public synchronized void setLostPacketNum(int lostPacketNum) {
		this.lostPacketNum.set(lostPacketNum);
	}
}
