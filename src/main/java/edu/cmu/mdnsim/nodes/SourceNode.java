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

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	private Map<String, SendRunnable> runningMap = new HashMap<String, SendRunnable>();
	
	public SourceNode() throws UnknownHostException {
		super();
	}	
	
	
	public void sendAndReportTest(String streamId, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new SendRunnable(streamId, destAddrStr, destPort, bytesToTransfer, rate));
	}
	
	@Override
	public void executeTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.executeTask(): Source received a work specification.");
		}
		for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {

			if (nodePropertiesMap.get("NodeId").equals(getNodeName())) {
				String[] ipAndPort = nodePropertiesMap.get("ReceiverIpPort").split(":");
				String destAddrStr = ipAndPort[0];
				int destPort = Integer.parseInt(ipAndPort[1]);
				int dataSize = Integer.parseInt(flow.getDataSize());
				int rate = Integer.parseInt(flow.getKiloBitRate());
				//Get up stream and down stream node ids
				//As of now Source Node does not have upstream id
				//upStreamNodes.put(streamSpec.StreamId, nodeProperties.get("UpstreamId"));
				downStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("DownstreamId"));
				
				try {

					SendRunnable sndThread = new SendRunnable(flow.getFlowId(), InetAddress.getByName(destAddrStr), destPort, dataSize, rate);
					runningMap.put(flow.getFlowId(), sndThread);
					WarpThreadPool.executeCached(sndThread);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	@Override
	public void terminateTask(Flow flow) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.terminateTask(): Source received terminate task.\n" + JSON.toJSON(flow));
		}
		
		SendRunnable thread = runningMap.get(flow.getFlowId());
		if(thread == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		thread.kill();
		
		releaseResource(flow);
	}
	
	@Override
	public void releaseResource(Flow flow) {
		
		SendRunnable sndThread = runningMap.get(flow.getFlowId());
		while (!sndThread.isStopped());
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.releaseResource(): Source starts to clean-up resource.");
		}
		
		sndThread.clean();
		runningMap.remove(flow.getFlowId());
		
		Map<String, String> nodeMap = flow.findNodeMap(getNodeName());
		
		try {
			msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

		
	}

	class SendRunnable extends NodeRunnable {
		
		private DatagramSocket sendSocket = null;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		
		public SendRunnable(String streamId, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate) {
			
			super(streamId);
			this.dstAddrStr = dstAddrStr;
			this.dstPort = dstPort;
			this.bytesToTransfer = bytesToTransfer;
			this.rate = rate;	
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
			double packetPerSecond = rate / NodePacket.PACKET_MAX_LENGTH;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 

			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException socketException) {
				socketException.printStackTrace();
			}
			
			if(!unitTest){
				report(EventType.SEND_START);
			}
			
			byte[] buf = null;
			int packetId = 0;
			try{
				while (!finished && !isKilled()) {
					
					long begin = System.currentTimeMillis();
					
					NodePacket nodePacket = bytesToTransfer <= NodePacket.PACKET_MAX_LENGTH ? new NodePacket(unitTest? 0: 1, packetId, bytesToTransfer) : new NodePacket(0, packetId);
	
					buf = nodePacket.serialize();	
		
					DatagramPacket packet = null;
					try {
						packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);
						sendSocket.send(packet);
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
					if(startedTime == 0){
						startedTime = System.currentTimeMillis();
					}
					bytesToTransfer -= packet.getLength();
					
					totalBytesSemaphore.acquire();
					totalBytesTranfered += packet.getLength();
					totalBytesSemaphore.release();
					
					if (unitTest) {
						System.out.println("[Source] " + totalBytesTranfered + " " + currentTime());
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
					packetId++;
					if(unitTest){
						packetId++;
					}
				}
			} catch(Exception e){
				e.printStackTrace();
			} finally{
				clean();
			}
			
			if(!unitTest){
				report(EventType.SEND_END);
			}
			
			if (ClusterConfig.DEBUG) {
				if (finished) {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has finished.");
				} else if (isKilled()){
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has been killed(not finished yet).");
				}
			}
			stop();
		}
		
		/**
		 * Clean up all resources for this thread.
		 */
		public void clean() {
			sendSocket.close();
			runningMap.remove(flowId);
		}
		
		private void report(EventType eventType){
			
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SourceNode.SendDataThread.run(): " + "Source will start sending data. " + "Record satrt time and report to master");
			}
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setFlowId(flowId);
			srcReportMsg.setTotalBytesTransferred(bytesToTransfer);
			srcReportMsg.setTime(Utility.currentTime());	
			srcReportMsg.setDestinationNodeId(downStreamNodes.get(flowId));
			srcReportMsg.setEventType(eventType);
			
			String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "/source_report", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
		}
	}	
}
