package edu.cmu.mdnsim.reporting;

import edu.cmu.mdnsim.nodes.NodePacket;

public class RateTracker {
	
	private long totalTransferredBytes = 0;
	
	private long startingTime = 0;
	
	private long punchTime = 0;
	
	private long punchBytes = 0;
	
	private boolean hasStarted = false;
	
	
	public synchronized void updateTracker(NodePacket packet) {
		if (!hasStarted) {
			startingTime = SystemClock.currentTimeMillis();
			punchTime    = startingTime;
		}
		totalTransferredBytes += packet.getDataLength();
		
	}
	
	public synchronized double getAverageRate() {
		return (double)totalTransferredBytes / (SystemClock.currentTimeMillis() - startingTime);
	}
	
	public synchronized double getInstanteRate() {
		long prevPunchTime  = punchTime;
		long prevPunchBytes = punchBytes;
		
		punchTime = SystemClock.currentTimeMillis();
		punchBytes = totalTransferredBytes;
		
		return (punchTime - prevPunchTime) != 0 ? 
				(double)(punchBytes-prevPunchBytes) / (double)(punchTime - prevPunchTime) : getAverageRate();
		
	}


	
	
}
