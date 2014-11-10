package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SourceAndSinkTest {
	
	public static void main(String[] args){
		
		SourceNode sourceNode = null;
		SinkNode sinkNode = null;
		try {
			sourceNode = new SourceNode();
			sinkNode = new SinkNode();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}
		
		String streamId = "stream-1";
		InetAddress sinkAddress =sinkNode.getHostAddr();
		int sinkPort = sinkNode.bindAvailablePortToFlow(streamId);
		int packageSize = 3000;
		int rate = 1000;
	
		sinkNode.receiveAndReportTest(streamId);	
		sourceNode.sendAndReportTest(streamId, sinkAddress, sinkPort, packageSize, rate);	
	}
}
