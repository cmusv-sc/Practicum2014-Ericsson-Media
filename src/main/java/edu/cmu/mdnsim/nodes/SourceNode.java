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
import com.ericsson.research.trap.utils.Future;
import com.ericsson.research.trap.utils.ThreadPool;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.concurrent.MDNTask;
import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	private Map<String, SendRunnable> streamIdToRunnableMap = new HashMap<String, SendRunnable>();
	
	public SourceNode() throws UnknownHostException {
		super();
	}	
	
	@Override
	public void executeTask(Flow flow) {
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.executeTask(): Source received a work specification.");
		}
		for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {
			if (nodePropertiesMap.get(Flow.NODE_ID).equals(getNodeId())) {
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
					createAndLaunchSendRunnable(flow, InetAddress.getByName(destAddrStr), destPort, dataSize, rate);					
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	/**
	 * Create and launch a SendRunnable and put it in the streamId - runnable map
	 * @param streamId
	 * @param destAddrStr
	 * @param destPort
	 * @param bytesToTransfer
	 * @param rate
	 */
	public void createAndLaunchSendRunnable(Flow flow, InetAddress destAddrStr, int destPort, int bytesToTransfer, int rate){
		
		SendRunnable sendRunnable = new SendRunnable(flow, destAddrStr, destPort, bytesToTransfer, rate);
		streamIdToRunnableMap.put(flow.getFlowId(), sendRunnable);

		if(integratedTest){
			ExecutorService executorService = Executors.newSingleThreadExecutor();
			executorService.submit(sendRunnable);
			executorService.shutdown();	
		} else{
			WarpThreadPool.executeCached(sendRunnable);			
		}
	}
	
	@Override
	public void terminateTask(Flow flow) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.terminateTask(): Source received terminate task.\n" + JSON.toJSON(flow));
		}
		
		SendRunnable thread = streamIdToRunnableMap.get(flow.getFlowId());
		if(thread == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		thread.kill();
		
		releaseResource(flow);
	}
	
	@Override
	public void releaseResource(Flow flow) {
		
		SendRunnable sndThread = streamIdToRunnableMap.get(flow.getFlowId());
		while (!sndThread.isStopped());
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]SourceNode.releaseResource(): Source starts to clean-up resource.");
		}
		
		sndThread.clean();
		streamIdToRunnableMap.remove(flow.getFlowId());
		
		Map<String, String> nodeMap = flow.findNodeMap(getNodeId());
		
		try {
			msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	private class SendRunnable extends NodeRunnable {
		
		private DatagramSocket sendSocket = null;
		private InetAddress dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		
		private DatagramPacket packet;
		
		public SendRunnable(Flow flow, InetAddress dstAddrStr, int dstPort, int bytesToTransfer, int rate) {
			
			super(flow);
			this.dstAddrStr = dstAddrStr;
			this.dstPort = dstPort;
			this.bytesToTransfer = bytesToTransfer;
			this.rate = rate;	
		}
		
		/**
		 * The method will send a packet in the following order:
		 * 1. Calculate the packet number per second based on the user specified sending rate.
		 * 2. Calculates the time expected to send one package in millisecond.
		 * 3. Send one packet, if the actual sending time is less than expected time, it will sleep for the gap
		 * 					   else, do nothing. In this case, the use specified rate is higher than the highest rate in real
		 * 
		 */
		@Override
		public void run() {
			
			if(!initializeSocketAndPacket()){
				return;
			}
			
			double packetPerSecond = rate / NodePacket.PACKET_MAX_LENGTH;
			long millisecondPerPacket = (long)(1 * edu.cmu.mdnsim.nodes.AbstractNode.MILLISECONDS_PER_SECOND / packetPerSecond); 

			if(!integratedTest){
				report(EventType.SEND_START);
			}
			
			Future reportFuture = null;	
			long startedTime = 0;
			int packetId = 0;
			
			while (bytesToTransfer > 0 && !isKilled()) {	
				long begin = System.currentTimeMillis();
				
				NodePacket nodePacket = bytesToTransfer <= NodePacket.PACKET_MAX_LENGTH ? new NodePacket(1, packetId, bytesToTransfer) : new NodePacket(0, packetId);
				packet.setData(nodePacket.serialize());
				try {
					sendSocket.send(packet);
				} catch (IOException e) {
					e.printStackTrace();
				}
	
				if(startedTime == 0){
					startedTime = System.currentTimeMillis();
					reportFuture = createAndLaunchReportTransportationRateRunnable();
				}
				
				bytesToTransfer -= packet.getLength();
				setTotalBytesTranfered(getTotalBytesTranfered() + packet.getLength());
			
				if (integratedTest) {
					System.out.println("[Source] " + getTotalBytesTranfered() + " " + Utility.currentTime());
				}
				
				long end = System.currentTimeMillis();
				long millisRemaining = millisecondPerPacket - (end - begin);
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
				packetId++;
			}
			
			if(!integratedTest){
				report(EventType.SEND_END);
				this.sendEndMessageToDownstream();
			} 
			
			if (ClusterConfig.DEBUG) {
				if (isKilled()) {
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has been killed(not finished yet).");
				} else{
					System.out.println("[DEBUG]SourceNode.SendDataThread.run():" + " This thread has finished.");
				}
			}
			
			if(reportFuture != null){
				reportFuture.cancel(true);
			}
			clean();
			stop();
		}
		
		
		

		/**
		 * Initialize the send socket and the DatagramPacket 
		 * @return true, successfully done
		 * 		   false, failed in some part
		 */
		private boolean initializeSocketAndPacket(){
			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException socketException) {
				return false;
			}
			
			byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH];
			packet = new DatagramPacket(buf, buf.length, dstAddrStr, dstPort);
			
			return true;	
		}
		
		/**
		 * Create and Launch a report thread
		 * @return Future of the report thread
		 */
		private Future createAndLaunchReportTransportationRateRunnable(){
			ReportRateRunnable reportTransportationRateRunnable = new ReportRateRunnable(INTERVAL_IN_MILLISECOND);
			if(integratedTest){
				ExecutorService executorService = Executors.newSingleThreadExecutor();
				Future reportFuture = (Future) executorService.submit(reportTransportationRateRunnable);
				executorService.shutdown();
				return reportFuture;
			} else {
				Future reportFuture = ThreadPool.executeAfter(new MDNTask(reportTransportationRateRunnable), 0);
				return reportFuture;
			}	
		}
		
		/**
		 * Clean up all resources for this thread.
		 */
		public void clean() {
			if(sendSocket != null && !sendSocket.isClosed()){
				sendSocket.close();
			}
			if(streamIdToRunnableMap.containsKey(getFlowId())){
				streamIdToRunnableMap.remove(getFlowId());
			}
		}
		
		private void report(EventType eventType){
			
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SourceNode.SendDataThread.run(): " + "Source will start sending data. " + "Record satrt time and report to master");
			}
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setFlowId(getFlowId());
			srcReportMsg.setTotalBytesTransferred(bytesToTransfer);
			srcReportMsg.setTime(Utility.currentTime());	
			srcReportMsg.setDestinationNodeId(downStreamNodes.get(getFlowId()));
			srcReportMsg.setEventType(eventType);
			
			String fromPath = "/" + SourceNode.this.getNodeId() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "/source_report", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
		}
	}	
}
