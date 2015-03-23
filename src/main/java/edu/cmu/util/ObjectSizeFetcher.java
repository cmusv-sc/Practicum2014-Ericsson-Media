package edu.cmu.util;

import java.lang.management.ManagementFactory;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

public class ObjectSizeFetcher {
    
	public static double getProcessCpuLoad() throws MalformedObjectNameException, ReflectionException, InstanceNotFoundException {

	    MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
	    ObjectName name    = ObjectName.getInstance("java.lang:type=OperatingSystem");
	    AttributeList list = mbs.getAttributes(name, new String[]{ "ProcessCpuLoad" });

	    if (list.isEmpty())     return Double.NaN;

	    Attribute att = (Attribute)list.get(0);
	    Double value  = (Double)att.getValue();
	    System.out.println("length: " + list.size());
	    if (value == -1.0)      return Double.NaN;  // usually takes a couple of seconds before we get real values

	    return ((int)(value * 1000) / 10.0);        // returns a percentage value with 1 decimal point precision
	}
	
	public static void main(String[] args) {
		Thread thCom = new Thread(new Consuming());
		Thread thCal = new Thread(new Calculating());
		thCom.start();
		thCal.start();
		
	}
	
	public static class Consuming implements Runnable {

		@Override
		public void run() {
			int i = 0;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			while(true) {
				i++;
			}
			
		}
		
	}
	
	public static class Calculating implements Runnable {

		@Override
		public void run() {
			
			while (true) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					System.out.println("CPU Usage: " + getProcessCpuLoad());
				} catch (MalformedObjectNameException | InstanceNotFoundException
						| ReflectionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
	}
}
