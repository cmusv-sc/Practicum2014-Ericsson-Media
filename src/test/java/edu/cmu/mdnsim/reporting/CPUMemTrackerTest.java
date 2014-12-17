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
		CPUMemTracker cpuMemTracker = new CPUMemTracker();
		cpuMemTracker.trackCPU();
		assertEquals("should return initial value because failed to use top command in Windows", cpuMemTracker.getCpuUsage(), -1, 0.001);
	}

	@Test
	public final void testTrackMem() {
		CPUMemTracker cpuMemTracker = new CPUMemTracker();
		cpuMemTracker.trackMem();
		assertEquals("should return initial value because failed to use top command in Windows", cpuMemTracker.getCpuUsage(), -1, 0.001);
	}

}
