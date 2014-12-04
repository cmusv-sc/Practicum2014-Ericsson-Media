package edu.cmu.mdnsim.nodes;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Was
 *
 */
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
		int sinkPort = sinkNode.getAvailablePort(streamId);
		int packageSize = 3000;
		int rate = 1000;
	
		//sinkNode.createAndLanchReceiveRunnable(streamId);	
		//sourceNode.createAndLaunchSendRunnable(streamId, sinkAddress, sinkPort, packageSize, rate);	
	}
}
