package edu.cmu.mdnsim.reporting;





/**
 * 
 * A tracker for packet loss. It calculates the packet lost number based on packet id given.
 * 
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
	
	private long rcvedPackedIdInPrevPeriod = 0;
	private long lostPakcetNumInPrevPeriod = 0;
	
	/**
	 * 
	 * The window size calculation helper. The window size is determined by the stream bit rate, time out and size of
	 * each packet.
	 * 
	 * @param		kbps		kilobits per second of the stream
	 * @param		timeout		the time in seconds after which the packet is regarded as lost if is not received
	 * 							<p>As MDNSim uses UDP as transport for media data, the packet might be received out-of-
	 * 							order or lost. The timeout indicates the maximum of delay of each packet. </p>
	 * @param		packetSize	the size in bytes of each packet
	 * @return
	 */
	public static int calculateWindowSize(int kbps, int timeout, int packetSize) {
		
		return kbps * 1000 * timeout / packetSize / 8;
		
	}
	
	/**
	 * 
	 * @param	windowSize	The size of the window. The larger the window is, the timeout for each packet is larger.
	 * 
	 */
	public PacketLostTracker(int windowSize){
		
//		File logFile = new File("sink -" + System.currentTimeMillis() + ".log");
//		try {
//			out = new FileOutputStream(logFile);
//		} catch (FileNotFoundException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		
		if(windowSize <= 0){
			throw new IllegalArgumentException("windowSize is less than or equal to 0");
		}
		this.windowSize = windowSize;
		buff = new long[windowSize];
		reset();
	}

	/**
	 * Update the receiving buffer with most recent received packetId.
	 * 
	 * @param	packetId	The packetId that is received
	 * 
	 */
	public synchronized void updatePacketLost(long packetId){ 
		
		StringBuffer sb = new StringBuffer();
		sb.append("[" + packetId + "]:\t");
		if(packetId < 0){
			throw new IllegalArgumentException("Invalid packed id. " + packetId);
		}
		
		/*
		 * A new packet arrives out of (larger) current window, do following steps:
		 * [1] calculate not RCV till minNonRecvedPacketId equals to packetId - windowSize;
		 * [2] set current window as [packetId - windowSize + 1, packetId];
		 */
		
		//If packetId = 14, minRcvedPacketId = 4, and windowSize = 10, slide window to [5, 14].
		if (minNonRcvedPacketId + windowSize <= packetId) {
			//Step[1]
			while(minNonRcvedPacketId + windowSize <= packetId) {
				long lastPacketId = buff[(int)(minNonRcvedPacketId % windowSize)];
				if (lastPacketId == NOT_RCVED) {
					packetLostCounter++;
					sb.append("![" + minNonRcvedPacketId + "] ");
				} else {
					buff[(int)(minNonRcvedPacketId % windowSize)] = NOT_RCVED;
				}
				incrementMinNonRcvedPacketId();
			}
			//Step[2]
			assert(packetId - windowSize + 1 == minNonRcvedPacketId);
			
		} 
		
		/*
		 * A new packet arrives out of (smaller) current window, the packet is timeout and regarded as a packet loss.
		 */
		else if (minNonRcvedPacketId > packetId){
//			sb.append("@[" + packetId + "]");
//			sb.append("minN[" + minNonRcvedPacketId + "] ");
//			try {
//				out.write((sb.toString() + "\n").getBytes());
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			return;
		} 
		
//		sb.append("minN[" + minNonRcvedPacketId + "] ");
//		try {
//			out.write((sb.toString() + "\n").getBytes());
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		/*
		 * A new packet arrives in current window, update the window and might slide
		 */
		buff[(int)(packetId % windowSize)] = packetId;
		maxRcvedPacketId = Math.max(packetId, maxRcvedPacketId);
		
	}
		
	public synchronized long getLostPacketNum() {
		return packetLostCounter;
	}

	
	public synchronized void reset() {
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
	
	/**
	 * 
	 * Get the average packet loss rate of the stream.
	 * 
	 * The divider is {@link minNonRcvedPacketId} because the area {0, {@link minNonRcvedPacketId}} contains the packets
	 * that are either received or regarded as lost or timeout.
	 * 
	 * The dividend is the return value of getLostPacketNum().
	 * 
	 * @return
	 */
	public synchronized double getAvrPacketLossRate() {
		if (this.minNonRcvedPacketId == 0) {
			return 0;
		} else {
			return ((double)packetLostCounter / (double)minNonRcvedPacketId);
		}
	}
	
	public synchronized double getInstantPacketLossRate() {
		
		if (minNonRcvedPacketId - rcvedPackedIdInPrevPeriod == 0) {
			return 0;
		} else {
			
			long lostPakcetNumInCurrPeriod = packetLostCounter - lostPakcetNumInPrevPeriod;
			lostPakcetNumInPrevPeriod = packetLostCounter;
			
			long rcvedPacketNumInCurrPeriod = minNonRcvedPacketId - rcvedPackedIdInPrevPeriod;
			rcvedPackedIdInPrevPeriod = minNonRcvedPacketId;
			
			return (double)lostPakcetNumInCurrPeriod / (double)rcvedPacketNumInCurrPeriod;
		}
		
		
	}
	
	
//	@Override
//	public String toString() {
//		StringBuffer sb = new StringBuffer();
//		sb.append("min_non_rcved_id: " + this.minNonRcvedPacketId + "\t");
//		sb.append("max_rcved_id: " + this.maxRcvedPacketId + "\t");
//		sb.append("\nBuff: [");
//		for (int i = 0; i < this.windowSize; i++) {
//			if (buff[i] == -1)
//				sb.append("_");
//				else
//			sb.append(this.buff[i]);
//			sb.append(",");
//		}
//		sb.deleteCharAt(sb.length()-1);
//		sb.append("]");
//		return sb.toString();
//	}
	
	
	private void incrementMinNonRcvedPacketId() {
		this.minNonRcvedPacketId++;
	}

}