package edu.cmu.mdnsim.reporting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * A tracker that can track the cpu and memory usage 
 *
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 */
public class CPUMemTracker {

	private double cpuUsage;
	private double memUsage;
	
	/**
	 * Retrieve CPU and Memory and update member variables.
	 */
	public void trackBoth(){
		trackCPU();
		trackMem();
	}
	/**
	 * Retrieve CPU usage and update the member variable.
	 */
	public void trackCPU(){
		String[] command = {"/bin/sh", "-c", "top -b -n 1 | grep Cpu"};
        Process process = null;
		try {
			process = Runtime.getRuntime().exec(command);
		} catch (IOException e1) {
			// Fail to get CPU usage in this call
			return;
		}
		
        try {
			process.waitFor();
		} catch (InterruptedException e) {
			// Interrupted
			return;
		}
        
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    	String str = null;
		try {
			str = stdInput.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(str != null){
			String cpuString = str.split(" ")[1].substring(0,4);
			setCpuUsage(Double.valueOf(cpuString));
		}
	}
	
	/**
	 * Retrieve the memory usage and update member variable
	 */
	public void trackMem(){
		String[] command = {"/bin/sh", "-c", "top -b -n 1 | grep Mem"};
        Process process = null;
		try {
			process = Runtime.getRuntime().exec(command);
		} catch (IOException e1) {
			// Fail to get CPU usage in this call
			return;
		}
		
        try {
			process.waitFor();
		} catch (InterruptedException e) {
			// Interrupted
			return;
		}
        
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
    	String str = null;
		try {
			str = stdInput.readLine();
		} catch (IOException e) {
			// Failed to read the line
			return;
		}
		if(str != null){
			String[] memStrings = str.split("\\s+");
			double total = Double.valueOf(memStrings[1].substring(0, memStrings[1].length() - 1));
			double used = Double.valueOf(memStrings[3].substring(0, memStrings[3].length() - 1));
			setMemUsage(used / total);
		}
	}
	
	public synchronized double getCpuUsage() {
		return cpuUsage;
	}

	public synchronized void setCpuUsage(double cpuUsage) {
		this.cpuUsage = cpuUsage;
	}
	
	public synchronized double getMemUsage() {
		return memUsage;
	}

	public synchronized void setMemUsage(double memUsage) {
		this.memUsage = memUsage;
	}
}
