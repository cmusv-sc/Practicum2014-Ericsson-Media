package edu.cmu.mdnsim.reporting;

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

public class CPUUsageTracker {
	
	OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
	
	@SuppressWarnings("restriction")
	public double getCPUUsage() {
		
		return osBean.getProcessCpuLoad();
	}
	
}
