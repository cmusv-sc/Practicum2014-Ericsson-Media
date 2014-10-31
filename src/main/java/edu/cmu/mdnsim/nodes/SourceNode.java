package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	/**
	 * This instance variable is used to control whether print out info which 
	 * is used in Hao's unit test.
	 */
	private boolean UNIT_TEST = false;
	
	
	private Map<String, SendThread> runningMap = new HashMap<String, SendThread>();
	
	public SourceNode() throws UnknownHostException {
		super();
	}	
	
	
	public void sendAndReportTest(String streamId, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new SendThread(streamId, destAddrStr, destPort, bytesToTransfer, rate));
	}
	
	@Override
	public void executeTask(StreamSpec streamSpec) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.executeTask(): Source received a work specification.");
		}
		for (HashMap<String, String> currentFlow : streamSpec.Flow) {

			if (currentFlow.get("NodeId").equals(getNodeName())) {
				String[] ipAndPort = currentFlow.get("ReceiverIpPort").split(":");
				String destAddrStr = ipAndPort[0];
				int destPort = Integer.parseInt(ipAndPort[1]);
				int dataSize = Integer.parseInt(streamSpec.DataSize);
				int rate = Integer.parseInt(streamSpec.ByteRate);
				try {
					SendThread sndThread = new SendThread(streamSpec.StreamId, InetAddress.getByName(destAddrStr), destPort, dataSize, rate);
					runningMap.put(streamSpec.StreamId, sndThread);
					WarpThreadPool.executeCached(sndThread);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	@Override
	public void terminateTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.terminateTask(): Source received terminate task.\n" + JSON.toJSON(streamSpec));
		}
		
		SendThread sndThread = runningMap.get(streamSpec.StreamId);
		sndThread.kill();
		
		releaseResource(streamSpec);
	}
	
	@Override
	public void releaseResource(StreamSpec streamSpec) {
		
		SendThread sndThread = runningMap.get(streamSpec.StreamId);
		while (!sndThread.isStopped());
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.releaseResource(): Source starts to clean-up resource.");
		}
		
		sndThread.clean();
		runningMap.remove(streamSpec.StreamId);
		
		for (int i = 0; i < streamSpec.Flow.size(); i++) {
			Map<String, String> nodeMap = streamSpec.Flow.get(i);
			if (nodeMap.get("NodeId").equals(getNodeName())) {
				
				try {
					msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", streamSpec);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
				
			}
		}
		
	}
	
	private class SendThread implements Runnable {
		
		private String streamId;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private DatagramSocket sourceSocket = null;
		
		private boolean killed = false;
		private boolean stopped = false;
		
		public SendThread(String streamId, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate) {
			
			SendThread.this.streamId = streamId;
			SendThread.this.dstAddrStr = dstAddrStr;
			SendThread.this.dstPort = dstPort;
			SendThread.this.bytesToTransfer = bytesToTransfer;
			SendThread.this.rate = rate;	
		}
		
		/**
		 * The method will send packet in the following order:
		 * 1. Calculate the packet number per second based on the user specified sending rate.
		 * 2. Calculates the time expected to send one package in millisecond.
		 * 3. Send one packet, if the actual sending time is less than expected time, it will sleep for the gap
		 * 					   else, do nothing. In this case, the use specified rate is higher than the highest rate in real
		 * 
		 */
		@Override
		public void run() {

			double packetPerSecond = rate / STD_DATAGRAM_SIZE;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 
			
			boolean finished = false;
			
			
			try {
				sourceSocket = new DatagramSocket();
			} catch (SocketException socketException) {
				socketException.printStackTrace();
			}
			
			
			report();
			
			byte[] buf = null;
			
			while (!finished && !isKilled()) {
				
				long begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? bytesToTransfer : STD_DATAGRAM_SIZE];
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);	
	
				DatagramPacket packet = null;
				try {
					packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				if (UNIT_TEST) {
					System.out.println("[Source] " + bytesToTransfer + " " + currentTime());
				}
				
				long end = System.currentTimeMillis();
				
				finished = (bytesToTransfer <= 0);
				
				long millisRemaining = millisecondPerPacket - (end - begin);
				
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
				
			}

			
			
			if (ClusterConfig.DEBUG) {
				if (finished) {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():"
							+ " This thread has finished.");
				} else if (isKilled()){
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():"
							+ " This thread has been killed(not finished yet).");
				}
			}
			
			stop();
			
		}
		
		/**
		 * Clean up all resources for this thread.
		 */
		public void clean() {
			sourceSocket.close();
		}
		
		private void report(){
			
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SourceNode.SendDataThread.run(): " + "Source will start sending data. " + "Record satrt time and report to master");
			}
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(streamId);
			srcReportMsg.setTotalBytesTransferred(bytesToTransfer);
			srcReportMsg.setStartTime(Utility.currentTime());	
			String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "/source_report", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
		}
		
		private synchronized void kill() {
			killed = true;
		}
		
		private synchronized boolean isKilled() {
			return killed;
		}
		
		private synchronized void stop() {
			stopped = true;
		}
		
		private synchronized boolean isStopped() {
			return stopped;
		}
	}


	
}
