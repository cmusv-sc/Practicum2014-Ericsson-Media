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
		int sinkPort = sinkNode.getAvailablePort(streamId);
		InetAddress processingAddress = processingNode.getHostAddr();
		int processingPort = processingNode.getAvailablePort(streamId);
		
/*		int totalDataSize = 6250000;
		int rate = 625000;*/
		
		int totalDataSize = 7000;
		int rate = 1000;
	
//		sinkNode.setUnitTest(true);
//		processingNode.setUnitTest(true);
//		sourceNode.setUnitTest(true);

		//sinkNode.createAndLanchReceiveRunnable(streamId);
		//processingNode.createAndLaunchReceiveProcessAndSendRunnable(streamId, totalDataSize, sinkAddress, sinkPort,processingLoop, processingSpaceInByte, rate);
		//sourceNode.createAndLaunchSendRunnable(streamId, processingAddress, processingPort, totalDataSize, rate);
	}
}
