package edu.cmu.mdnsim.nodes;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tracker for packet lost at nodes. It will calculate the packet lost number based on packet id given in two update methods.
 * 
 * This class is not thread safe except getter and setter of lostPacketNum and highestPacketId.
 * All applicable methods throw a NullPointerException if null is passed in any parameter
 */
public class PacketLostTracker {
	
	private AtomicInteger lostPacketNum;
	private AtomicInteger highestPacketId;

	private int expectedMaxPacketId;
	private int packetNumInAWindow;
	private int lowPacketIdBoundry;
	private int highPacketIdBoundry;
	private int receivedPacketNumInAWindow;
	private boolean finished;

	/**
	 * Construct an instance based on five parameters.
	 * @param totalData, total data for this flow in byte
	 * @param rate, transfer rate of the flow in byte
	 * @param packetLength, length of the packet for transfer
	 * @param timeout, longest time to wait when no packets comes, this can help calculate window size
	 * @throws IllegalArgumentException if any of the four parameters is invalid
	 */
	public PacketLostTracker(int totalData, int rate, int packetLength, int timeout, int beginId){
		
		if(totalData < 0 || rate < 0 || packetLength < 0 || timeout < 0 || beginId < 0){
			throw new IllegalArgumentException();
		}

		lostPacketNum = new AtomicInteger(0);
		expectedMaxPacketId = (int) Math.ceil(totalData * 1.0 / packetLength) - 1;
		double packetNumPerSecond = rate * 1.0 / packetLength;
		packetNumInAWindow = (int) Math.ceil(packetNumPerSecond * timeout / 1000);
		lowPacketIdBoundry = beginId;
		highPacketIdBoundry = Math.min(beginId + packetNumInAWindow - 1, expectedMaxPacketId);
		receivedPacketNumInAWindow = 0;
		highestPacketId = new AtomicInteger(-1);
		
		finished = false;
	}

	/**
	 * Judge the status of the packet based on id and update the packet lost according to 3 situations
	 * @param packetId, equal or above 0 and equal or lower than max expected id
	 * @throws IllegalArgumentException if packetId is not in valid range which is [0, expectedMaxPacketId]
	 * @throws IllegalStateException if called after timeout
	 */
	public void updatePacketLost(int packetId){
		if(packetId < 0 || packetId > expectedMaxPacketId){
			throw new IllegalArgumentException("packet id " + packetId + " is out of valid range [0," + expectedMaxPacketId +"]");
		}
		if(finished){
			throw new IllegalStateException("Timeout has happened.");
		}
		
		if(packetId > highPacketIdBoundry){
			setLostPacketNum(this.getLostPacketNum() + (highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow) + (packetId - highPacketIdBoundry - 1));
			lowPacketIdBoundry = packetId;
			highPacketIdBoundry = Math.min(lowPacketIdBoundry + packetNumInAWindow - 1, expectedMaxPacketId);
			receivedPacketNumInAWindow = 1;
		} else if(packetId >= lowPacketIdBoundry && packetId <= highPacketIdBoundry){
			receivedPacketNumInAWindow++;
		}
		
		setHighestPacketId(Math.max(getHighestPacketId(), packetId));
	}
	
	/**
	 * Update the packet lost for the last time. Timeout and last packet is received can lead this method being called. 
	 */
	public void updatePacketLostForLastTime(){
		int lostPacketNumInCurrentWindow = highPacketIdBoundry - lowPacketIdBoundry + 1 - receivedPacketNumInAWindow;
		int lostPacketNumInFollowingWindows = expectedMaxPacketId - highPacketIdBoundry;
		setLostPacketNum(getLostPacketNum() + lostPacketNumInCurrentWindow + lostPacketNumInFollowingWindows);
		
		finished = true;
	}
	
	public int getLostPacketNum() {
		return lostPacketNum.get();
	}

	public void setLostPacketNum(int lostPacketNum) {
		this.lostPacketNum.set(lostPacketNum);
	}
	
	public int getHighestPacketId() {
		return highestPacketId.get();
	}

	public void setHighestPacketId(int highestPacketId) {
		this.highestPacketId.set(highestPacketId);
	}
}