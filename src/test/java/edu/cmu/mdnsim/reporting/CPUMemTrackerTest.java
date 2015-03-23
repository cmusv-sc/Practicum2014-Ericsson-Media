package edu.cmu.mdnsim.reporting;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class CPUMemTrackerTest {

	
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public final void testTrackCPU() {
		CPUUsageTracker cpuMemTracker = new CPUUsageTracker();
		cpuMemTracker.trackCPU();
		assertEquals("should return initial value because failed to use top command in Windows", cpuMemTracker.getCpuUsage(), -1, 0.001);
	}

	@Test
	public final void testTrackMem() {
		CPUUsageTracker cpuMemTracker = new CPUUsageTracker();
		cpuMemTracker.trackMem();
		assertEquals("should return initial value because failed to use top command in Windows", cpuMemTracker.getCpuUsage(), -1, 0.001);
	}

}
