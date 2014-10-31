package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.util.Utility;

public class SinkNode extends AbstractNode {
	
	/**
	 * This instance variable is used to control whether print out info which 
	 * is used in Hao's unit test.
	 */
	private boolean UNIT_TEST = false;
	
	/* Key: stream ID; Value: DatagramSocket */
	private HashMap<String, DatagramSocket> streamSocketMap;
	
	
	/* Key: stream ID; Value: ReceiveThread */
	private Map<String, ReceiveThread> runningThreadMap;
	
	public SinkNode() throws UnknownHostException {
		super();
		streamSocketMap = new HashMap<String, DatagramSocket>();
		runningThreadMap = new ConcurrentHashMap<String, ReceiveThread>();
	}
	

	@Override
	public void executeTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.executeTask(): Sink received a StreamSpec.");
		}
		
		int flowIndex = -1;

		for (HashMap<String, String> currentFlow : streamSpec.Flow) {
			flowIndex++;
			if (currentFlow.get("NodeId").equals(getNodeName())) {
				Integer port = bindAvailablePortToStream(streamSpec.StreamId);
				ReceiveThread rcvThread = new ReceiveThread(streamSpec.StreamId);
				runningThreadMap.put(streamSpec.StreamId, rcvThread);
				WarpThreadPool.executeCached(rcvThread);
				
				if (flowIndex+1 < streamSpec.Flow.size()) {
					HashMap<String, String> upstreamFlow = streamSpec.Flow.get(flowIndex+1);
					upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
					try {
						msgBusClient.send("/tasks", currentFlow.get("UpstreamUri") + "/tasks", "PUT", streamSpec);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
				}
				break;
			}
			
		}

	}
	
	/**
	 * Creates a DatagramSocket and binds it to any available port
	 * The streamId and the DatagramSocket are added to a 
	 * HashMap<streamId, DatagramSocket> in the MdnSinkNode object
	 * 
	 * @param streamId
	 * @return port number to which the DatagramSocket is bound to
	 * -1 if DatagramSocket creation failed
	 * 0 if DatagramSocket is created but is not bound to any port
	 */
	
	public int bindAvailablePortToStream(String streamId) {
	
		if (streamSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():" + "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamSocketMap.get(streamId).getPort();
		} else {
			DatagramSocket udpSocket = null;
			try {
				udpSocket = new DatagramSocket(0, super.getHostAddr());
			} catch (SocketException e) {
				//TODO: Handle the exception. We may consider throw this exception
				e.printStackTrace();
			}
			streamSocketMap.put(streamId, udpSocket);
			return udpSocket.getLocalPort();
		}
	}
	
	
	public void receiveAndReportTest(String streamId){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveThread(streamId));
	}
	
	/**
	 * 
	 * Each stream is received in a separate WarpPoolThread.
	 * After receiving all packets from the source, this thread 
	 * reports the total time and total number of bytes received by the 
	 * sink node back to the master using the message bus.
	 * 
	 * @param streamId The streamId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	private class ReceiveThread implements Runnable {
		
		private String streamId;
		private int totalBytes = 0;
		private long startTime = 0;
		private long totalTime = 0;
		private DatagramSocket socket = null;

		private boolean killed = false;
		private boolean stopped = false;
		
		public ReceiveThread(String streamId) {
			this.streamId = streamId;
		}
		
		@Override

		public void run() {				

			long startTime = 0;
			int totalBytes = 0;

			
			socket = streamSocketMap.get(streamId);
			if (socket == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] SinkNode.ReceiveDataThread.run():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return;
			}
			
			byte[] buf = new byte[STD_DATAGRAM_SIZE]; 
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			
			boolean finished = false;
			
			while (!isKilled() && !finished) {
				try {	
					socket.receive(packet);
					startTime = System.currentTimeMillis();
					totalBytes += packet.getLength();	
					if (UNIT_TEST) {
						System.out.println("[Sink] " + totalBytes + " bytes received at " + currentTime());		
					}
					
					finished = (packet.getData()[0] == 0);

				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}	
			
			long endTime= System.currentTimeMillis();
			
			stop();
			
			if (ClusterConfig.DEBUG) {
				if (finished) {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Finish receiving.");
				} else if (killed) {
					System.out.println("[DEBUG]SinkNode.ReceiveThread.run(): Killed.");
				} else {
					System.err.println("[DEBUG]SinkNode.ReceiveThread.run(): Unexpected.");
				}
			}
			
			report(startTime, endTime, totalBytes);
				
		}
		
		private void report(long startTime, long endTime, int totalBytes){

			SinkReportMessage sinkReportMsg = new SinkReportMessage();
			sinkReportMsg.setStreamId(streamId);
			sinkReportMsg.setTotalBytes(totalBytes);
			sinkReportMsg.setEndTime(Utility.millisecondTimeToString(endTime));
			
			String fromPath = SinkNode.super.getNodeName() + "/finish-rcv";
			
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG]SinkNode.ReceiveThread.report(): Sink sends report to master.");
			}
			
			try {
				msgBusClient.sendToMaster(fromPath, "/sink_report", "POST", sinkReportMsg);
			} catch (MessageBusException e) {
				//TODO: add exception handler
				e.printStackTrace();
			}
			
			if (ClusterConfig.DEBUG) {
				System.out.println("[INFO]SinkNode.ReceiveDataThread.run(): " + "Sink finished receiving data at Stream-ID "+sinkReportMsg.getStreamId()+
					" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+ (endTime - startTime));
			}
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
		
		private void clean() {
			socket.close();
			streamSocketMap.remove(streamId);
		}
	}

	@Override
	public void terminateTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.terminateTask(): " + JSON.toJSON(streamSpec));
		}
		
		for (int i = 0; i < streamSpec.Flow.size(); i++) {
			
			Map<String, String> nodeMap = streamSpec.Flow.get(i);
			
			ReceiveThread rcvThread = runningThreadMap.get(streamSpec.StreamId);
			rcvThread.kill();
			/* Find the Map for current node */
			if (nodeMap.get("NodeId").equals(getNodeName())) {
				try {
					msgBusClient.send("/tasks", nodeMap.get("UpstreamUri") + "/tasks", "POST", streamSpec);
				} catch (MessageBusException e) {
					e.printStackTrace();
				}
			}
			
		}
		
	}

	public void setUnitTest(boolean unitTest) {
		this.UNIT_TEST = unitTest;
	}


	@Override
	public void releaseResource(StreamSpec streamSpec) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.releaseResource(): Sink starts to clean-up resource.");
		}
		
		ReceiveThread rcvThread = runningThreadMap.get(streamSpec.StreamId);
		while (!rcvThread.isStopped());
		rcvThread.clean();
		runningThreadMap.remove(streamSpec.StreamId);
	}
	

	
	
}
