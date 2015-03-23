package edu.cmu.mdnsim.reporting;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class MemUsageTracker {
	
	MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
	
	/**
	 * Get the used memory in MB of JVM.
	 * @return
	 */
	public long getMemUsage() {
		
		MemoryUsage memoryUsage = memoryBean.getHeapMemoryUsage();
		return memoryUsage.getUsed() / 1024 / 1024;
	}
	
}
