package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SourceProcessAndSinkTest {

	public static void main(String[] args) {
		
		SourceNode sourceNode = null;
		ProcessingNode processingNode = null;
		SinkNode sinkNode = null;
		long processingLoop = 30000;
		int processingSpaceInByte = 1000;
		try {
			sourceNode = new SourceNode();
			processingNode = new ProcessingNode();
			sinkNode = new SinkNode();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return;
		}
		
		String streamId = "stream-1";
		InetAddress sinkAddress = sinkNode.getHostAddr();
		int sinkPort = sinkNode.bindAvailablePortToFlow(streamId);
		InetAddress processingAddress = processingNode.getHostAddr();
		int processingPort = processingNode.bindAvailablePortToFlow(streamId);
		
		int totalDataSize = 3500;
		int rate = 1000;
	
		sinkNode.setUnitTest(true);
		processingNode.setUnitTest(true);
		sourceNode.setUnitTest(true);

		sinkNode.receiveAndReportTest(streamId);
		processingNode.receiveProcessAndSendTest(streamId, totalDataSize, sinkAddress, sinkPort,processingLoop, processingSpaceInByte);
		sourceNode.sendAndReportTest(streamId, processingAddress, processingPort, totalDataSize, rate);	
	}
}
