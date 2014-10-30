package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SourceProcessAndSinkTest {

	public static void main(String[] args){
		
		SourceNode sourceNode = null;
		ProcessingNode processingNode = null;
		SinkNode sinkNode = null;
		long processingLoop = 30000;
		int processingSpaceInByte = 1000;
		try {
			sourceNode = new SourceNode();
			processingNode = new ProcessingNode(processingLoop, processingSpaceInByte);
			sinkNode = new SinkNode();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		String streamId = "stream-1";
		InetAddress sinkAddress = sinkNode.getHostAddr();
		int sinkPort = sinkNode.bindAvailablePortToStream(streamId);
		InetAddress processingAddress = processingNode.getHostAddr();
		int processingPort = processingNode.bindAvailablePortToStream(streamId);
		
		int packageSize = 3000;
		int rate = 1000;
	
		sinkNode.receiveAndReportTest(streamId);
		processingNode.receiveProcessAndSendTest(streamId, sinkAddress, sinkPort);
		sourceNode.sendAndReportTest(streamId, processingAddress, processingPort, packageSize, rate);	
	}
}
