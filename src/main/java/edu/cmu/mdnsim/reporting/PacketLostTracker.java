package edu.cmu.mdnsim.reporting;

import java.util.Date;


/**
 * A tracker for packet lost at nodes. It will calculate the packet lost number based on packet id given in two update methods.
 * 
 * This class is not thread safe except getter and setter of lostPacketNum and highestPacketId.
 * All applicable methods throw a NullPointerException if null is passed in any parameter
 *
 * @author Geng Fu
 *
 */
public class PacketLostTracker {
	
	private int windowSize;
	private long[] buff;
	private long minNonRcvedPacketId = 0;
	private long maxRcvedPacketId = 0;
	
	private long packetLostCounter = 0L;
	
	
	
	private final long NOT_RCVED = -1;
	
	


	/**
	 * 
	 * @param dataSize Total data for this flow （unit: byte)
	 * @param rate Transfer rate of the flow (unit: bytes per second)
	 * @param packetSize size of the packet for transfer (unit: byte)
	 * @param timeout longest time to wait when no packets comes, this can help calculate window size
	 * @throws IllegalArgumentException if any of the four parameters is invalid
	 */
	public PacketLostTracker(int windowSize){
		
		if(windowSize <= 0){
			throw new IllegalArgumentException();
		}
		this.windowSize = windowSize;
		buff = new long[windowSize];
		reset();

		
	}

	/**
	 * Judge the status of the packet based on id and update the packet lost according to 3 situations
	 * @param packetId, equal or above 0 and equal or lower than max expected id
	 * @throws IllegalArgumentException if packetId is not in valid range which is [0, expectedMaxPacketId]
	 * @throws IllegalStateException if called after timeout
	 */
	public synchronized void updatePacketLost(long packetId){ 
		
		if (packetId == 0) {
			System.out.println("Recived 0.");
		}
		
		if(packetId < 0){
			throw new IllegalArgumentException("Invalid packed id. " + packetId);
		}
		
		/*
		 * A new packet arrives out of (larger) current window, do following steps:
		 * [1] calculate not RCV till minNonRecvedPacketId to packetId - windowSize;
		 * [2] set current window to packetId - windowSize + 1;
		 */
		if (minNonRcvedPacketId + windowSize <= packetId) { //If packetId = 14, minRcvedPacketId = 4, slide to at least [5 - 14]
			//Step[1]
			while(minNonRcvedPacketId + windowSize <= packetId) {
				long lastPacketId = buff[(int)(minNonRcvedPacketId % windowSize)];
				if (lastPacketId == NOT_RCVED) {
					packetLostCounter++;
				} else {
					buff[(int)(minNonRcvedPacketId % windowSize)] = NOT_RCVED;
				}
				minNonRcvedPacketId++;
				assert(packetId - windowSize + 1 == minNonRcvedPacketId);
			}
			
		} 
		/*
		 * A new packet arrives out of (smaller) current window, ignore
		 */
		else if (minNonRcvedPacketId > packetId){
			System.out.println("Out-of-order pakcet: " + packetId);
			return;
		} 
		/*
		 * A new packet arrives in current window, update the window and might slides
		 */
		
		buff[(int)(packetId % windowSize)] = packetId;
		maxRcvedPacketId = Math.max(packetId, maxRcvedPacketId);
		slide();
		
	}
		
	public synchronized long getLostPacketNum() {
		int packetLostCounterInCurrWindow = 0;
		for (long i = minNonRcvedPacketId; i <= maxRcvedPacketId; i++) {
			long lastPacketId = buff[(int)(i % windowSize)];
			if (lastPacketId == NOT_RCVED || lastPacketId < minNonRcvedPacketId) {
				packetLostCounterInCurrWindow++;
			}
		}
		System.out.println("min: " + minNonRcvedPacketId + "\tmax: " + maxRcvedPacketId);
		return packetLostCounter + packetLostCounterInCurrWindow;
	}

	
	public void reset() {
		for (int i = 0; i < windowSize; i++) {
			buff[i] = NOT_RCVED;
		}
		minNonRcvedPacketId = 0;
		maxRcvedPacketId = 0;
		packetLostCounter = 0L;
	}
	
	public synchronized long getHighestPacketId() {
		return this.maxRcvedPacketId;
	}
	
	private void slide() {
		while(buff[(int)(minNonRcvedPacketId % windowSize)] != NOT_RCVED && minNonRcvedPacketId <= maxRcvedPacketId) {
			buff[(int)(minNonRcvedPacketId % windowSize)] = NOT_RCVED;
			minNonRcvedPacketId++;
//			if (minNonRcvedPacketId % windowSize == 0) {
//				System.err.println(new Date() + ":\tA new window started");
//			}
		}
	}

}