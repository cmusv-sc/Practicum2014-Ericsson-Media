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
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.util.Utility;

public class SinkNode extends AbstractNode {
	
	/* Key: stream ID; Value: ReceiveThread */
	private Map<String, ReceiveRunnable> runningThreadMap = new ConcurrentHashMap<String, ReceiveRunnable>();
	
	public SinkNode() throws UnknownHostException {
		super();
	}
	

	@Override
	public void executeTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.executeTask(): Sink received a StreamSpec.");
		}
		
		int flowIndex = -1;

		for (HashMap<String, String> nodePropertiesMap : streamSpec.Flow) {
			flowIndex++;
			if (nodePropertiesMap.get("NodeId").equals(getNodeName())) {
				Integer port = bindAvailablePortToStream(streamSpec.StreamId);
				ReceiveRunnable rcvThread = new ReceiveRunnable(streamSpec.StreamId);
				runningThreadMap.put(streamSpec.StreamId, rcvThread);
				//Get up stream and down stream node ids
				//As of now Sink Node does not have downstream id
				upStreamNodes.put(streamSpec.StreamId, nodePropertiesMap.get("UpstreamId"));
				//downStreamNodes.put(streamSpec.StreamId, nodeProperties.get("DownstreamId"));
				
				WarpThreadPool.executeCached(rcvThread);
				
				if (flowIndex+1 < streamSpec.Flow.size()) {
					HashMap<String, String> upstreamFlow = streamSpec.Flow.get(flowIndex+1);
					upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
					try {
						msgBusClient.send("/tasks", nodePropertiesMap.get("UpstreamUri") + "/tasks", "PUT", streamSpec);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
				}
				break;
			}
			
		}

	}
	
	
	public void receiveAndReportTest(String streamId){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveRunnable(streamId));
	}
	
	@Override
	public void terminateTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.terminateTask(): " + JSON.toJSON(streamSpec));
		}
		
		ReceiveRunnable thread = runningThreadMap.get(streamSpec.StreamId);
		if(thread == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		thread.kill();
		
		Map<String, String> nodeMap = streamSpec.findNodeMap(getNodeName());
		
		try {
			msgBusClient.send("/tasks", nodeMap.get("UpstreamUri") + "/tasks", "POST", streamSpec);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
		
	}


	@Override
	public void releaseResource(StreamSpec streamSpec) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SinkNode.releaseResource(): Sink starts to clean-up resource.");
		}
		
		ReceiveRunnable rcvThread = runningThreadMap.get(streamSpec.StreamId);
		while (!rcvThread.isStopped());
		rcvThread.clean();
		runningThreadMap.remove(streamSpec.StreamId);
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
	private class ReceiveRunnable implements Runnable {
		
		private String streamId;
		private DatagramSocket socket = null;

		private boolean killed = false;
		private boolean stopped = false;
		
		public ReceiveRunnable(String streamId) {
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
					NodePacket nodePacket = new NodePacket(packet.getData());
					if (startTime == 0) {
						startTime = System.currentTimeMillis();
					}
					totalBytes += packet.getLength();	
					if (unitTest) {
						System.out.println("[Sink] " + totalBytes + " bytes received at " + currentTime());		
					}
					
					finished = nodePacket.isLast();

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
			
			if(!unitTest){
				report(startTime, endTime, totalBytes);
			}
				
		}
		
		private void report(long startTime, long endTime, int totalBytes){

			SinkReportMessage sinkReportMsg = new SinkReportMessage();
			sinkReportMsg.setStreamId(streamId);
			sinkReportMsg.setTotalBytes(totalBytes);
			sinkReportMsg.setTime(Utility.millisecondTimeToString(endTime));
			sinkReportMsg.setDestinationNodeId(upStreamNodes.get(streamId));
			sinkReportMsg.setEventType(EventType.RECEIVE_END);
			
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
				System.out.println("[INFO]SinkNode.ReceiveDataThread.run(): " 
						+ "Sink finished receiving data at Stream-ID " 
						+ sinkReportMsg.getStreamId()
						+ " Total bytes " + sinkReportMsg.getTotalBytes() 
						+ " Total Time:" + ((endTime - startTime) / 1000)
						+ "(sec)");
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
}
