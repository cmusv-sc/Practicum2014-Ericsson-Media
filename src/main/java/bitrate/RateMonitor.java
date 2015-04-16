package bitrate;

import edu.cmu.mdnsim.nodes.NodePacket;

/**
 * 
 * This class is used to adjust the transferring rates in the pace of seconds.
 * Source is supposed to call the {@link #updateRunningNspp(long) updateRunningNspp}
 * every time the source sends a packet and obtain the nanoseconds per packet.
 * The return values keeps changing on one second basis to adjust the transfer rate to the target one.
 * 
 * @author Geng Fu
 *
 */
public class RateMonitor {
	
	/**
	 * The starting time-stamp of current second
	 */
	private long lastSecondTimestmp;
	
	/**
	 * The total bytes sent in current second
	 */
	private long lastSecondSentBytes;

	/**
	 * The target rate(bytes per second) to achieve by rate correction
	 */
	private long targetRate;
	
	/**
	 * The computed rate (bytes per second) in runtime to compensate the deficit
	 * of target rate and actual transferring rate because of system.
	 */
	private long runningRate;

	/**
	 * The accumulative deficit. This should aim to eliminated by 
	 */
	private long cumulativeDeficit = 0;
	
	/**
	 * Nanoseconds per packet
	 */
	private long nspp;
	
	public RateMonitor(int targetRate) {
		this.targetRate = targetRate;
		this.runningRate = this.targetRate;
	}
	
	public void start() {
		lastSecondTimestmp = System.currentTimeMillis();
		lastSecondSentBytes = 0;
		nspp = bps2nspp((int)runningRate);
	}
	
	public long updateRunningNspp(long packetLength) {
		
		lastSecondSentBytes += packetLength;
		
		long currentTimeMillis = System.currentTimeMillis();
		
		if ((currentTimeMillis - lastSecondTimestmp) > 1000) {
			
			long lastSecondRate = lastSecondSentBytes / (currentTimeMillis - lastSecondTimestmp) * 1000;				

			long cDeficit = targetRate - lastSecondRate;
			cumulativeDeficit += cDeficit;
			
			//The offset cannot be less than -targeRate because the runningRate should be non-negative.
			long offset = Math.max(-targetRate, cumulativeDeficit);
			
//			logger.debug(String.format("RateMonitor.updateRunningNspp(): target:%d, running:%d, observed:%d, offset:%d, cumulativeOffset:%d", targetRate, runningRate, lastSecondRate, offset, this.cumulativeDeficit));
			
			//Adjust the running rate to meet the target rate on average.
			runningRate = targetRate + offset;
			
			lastSecondSentBytes = 0;
			lastSecondTimestmp = System.currentTimeMillis();
			nspp = bps2nspp((int)runningRate);
			
		}
		
		return nspp;
	}
	
	/**
	 * Convert the rate in bits per second to nanoseconds per packet
	 * 
	 * @param rate The sending rate in unit bps
	 * 
	 * @return
	 */
	private static long bps2nspp(int rate) {
		double packetsPerSecond = rate / NodePacket.MAX_PACKET_LENGTH;
		return (long)(edu.cmu.mdnsim.nodes.AbstractNode.NANOSCONDS_PER_SECOND / packetsPerSecond);	
	}
	
}
