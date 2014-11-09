package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.Flow;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.util.Utility;

public class ProcessingNode extends AbstractNode {

	private Map<String, ReceiveProcessAndSendRunnable> runningMap = new HashMap<String, ReceiveProcessAndSendRunnable>();

	public ProcessingNode() throws UnknownHostException {
		super();
	}

	@Override
	public void executeTask(Flow flow) {

		int flowIndex = -1;

		for (Map<String, String> nodePropertiesMap : flow.getNodeList()) {

			flowIndex++;

			if (nodePropertiesMap.get("NodeId").equals(getNodeName())) {

				/* Open a socket for receiving data from upstream node */
				int port = bindAvailablePortToFlow(flow.getFlowId());
				if(port == 0){
					//TODO, report to the management layer, we failed to bind a port to a socket
				}

				/* Get processing parameters */
				long processingLoop = Long.valueOf(nodePropertiesMap.get("ProcessingLoop"));
				int processingMemory = Integer.valueOf(nodePropertiesMap.get("ProcessingMemory"));

				//Get up stream and down stream node ids
				upStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("UpstreamId"));
				downStreamNodes.put(flow.getFlowId(), nodePropertiesMap.get("DownstreamId"));

				/* Get the IP:port */
				String[] addressAndPort = nodePropertiesMap.get("ReceiverIpPort").split(":");

				InetAddress targetAddress = null;
				try {
					targetAddress = InetAddress.getByName(addressAndPort[0]);
					int targetPort = Integer.valueOf(addressAndPort[1]);
					ReceiveProcessAndSendRunnable thread = new ReceiveProcessAndSendRunnable(flow.getFlowId(), Integer.valueOf(flow.getDataSize()), targetAddress, targetPort, processingLoop, processingMemory);
					runningMap.put(flow.getFlowId(), thread);
					WarpThreadPool.executeCached(thread);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}

				if (nodePropertiesMap.get("UpstreamUri") != null){
					try {
						Map<String, String> upstreamFlow = flow.getNodeList().get(flowIndex+1);
						upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port);
						msgBusClient.send("/tasks", nodePropertiesMap.get("UpstreamUri")+"/tasks", "PUT", flow);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
				}
				break;
			}
		}	
	}

	/**
	 * For Unit Test
	 */
	public void receiveProcessAndSendTest(String streamId, int totalData, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveProcessAndSendRunnable(streamId, totalData, destAddress, dstPort, processingLoop, processingMemory));
	}

	@Override
	public void terminateTask(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received terminate request.");
		}

		ReceiveProcessAndSendRunnable thread = runningMap.get(flow.getFlowId());
		if(thread == null){
			throw new TerminateTaskBeforeExecutingException();
		}
		thread.kill();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeName());

		try {
			msgBusClient.send("/tasks", nodeMap.get("UpstreamUri") + "/tasks", "POST", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void releaseResource(Flow flow) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received clean resource request.");
		}

		ReceiveProcessAndSendRunnable thread = runningMap.get(flow.getFlowId());
		while (!thread.isStopped());
		thread.clean();

		Map<String, String> nodeMap = flow.findNodeMap(getNodeName());
		try {
			msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", flow);
		} catch (MessageBusException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * Each stream is received in a separate WarpPoolThread.
	 * After receiving all packets from the source, this thread 
	 * reports the total time and total number of bytes received by the 
	 * sink node back to the master using the message bus.
	 * 
	 * @param flowId The streamId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	private class ReceiveProcessAndSendRunnable extends NodeRunnable {

		private int totalData;
		private DatagramSocket receiveSocket;

		private long processingLoop;
		private int processingMemory;
		private InetAddress dstAddress;
		private int dstPort;
		private DatagramSocket sendSocket;

		public ReceiveProcessAndSendRunnable(String streamId, int totalData, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory) {

			super(streamId);
			
			this.totalData = totalData;
			this.dstAddress = destAddress;
			this.dstPort = dstPort;
			this.processingLoop = processingLoop;
			this.processingMemory = processingMemory;
		}

		/**
		 * For packet lost statistical information:
		 * When a new packet is received, there are three status:
		 * - NEW, this packet is with the highest id among all the received packet
		 * - WAITING, this packet is added into waiting to be lost map when some high id packet was received
		 * - LOST, this packet is added to the lost set by the timer in the waiting map because of time out of wating
		 * 
		 * Assumption: The last packet that contains termination information must not be lost in this implementation
		 */
		@Override
		public void run() {

			if ((receiveSocket = flowIdToSocketMap.get(flowId)) == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] ProcNode.ReceiveProcessAndSendThread.run():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return;
			}

			try {
				sendSocket = new DatagramSocket();
			} catch (SocketException se) {
				se.printStackTrace();
			}

			byte[] buf = new byte[NodePacket.PACKET_MAX_LENGTH]; 
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			boolean sendStarted = false;
			boolean finished = false;
			
			/* These variables are for tracking packet lost */
			//int totalPacketNum = (int) Math.ceil(totalData / NodePacket.PACKET_MAX_LENGTH);
			HashSet<Integer> lostPacketIdSet = new HashSet<Integer>();
			HashMap<Integer, Timer> packetIdToTimerMap = new HashMap<Integer, Timer>();
			int highestReceivedPacketId = -1;
			
			try{
				while (!finished && !isKilled()) {
					try {
						receiveSocket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if(startedTime == 0) {
						startedTime = System.currentTimeMillis();
						//Report to Master that RECEIVE has Started
						if(!unitTest){
							report(startedTime,upStreamNodes.get(flowId),EventType.RECEIVE_START);
						}
					}

					byte[] rawData = packet.getData();
					NodePacket nodePacket = new NodePacket(rawData);
					
					int packetId = nodePacket.getMessageId();
					NewArrivedPacketStatus newArrivedPacketStatus = getNewArrivedPacketStatus(lostPacketIdSet, packetIdToTimerMap, packetId);
					switch(newArrivedPacketStatus){
						case LOST:
							continue;
						case WAIT:
							Timer timer = packetIdToTimerMap.get(packetId);
							timer.cancel();
							packetIdToTimerMap.remove(packetId);
							break;
						case NEW:
							for(int i = highestReceivedPacketId + 1; i < packetId; i++){
								AddPacketIdToLostSetTask task = new AddPacketIdToLostSetTask(i, lostPacketIdSet);
								Timer newTimer = new Timer();
								newTimer.schedule(task, MAX_WAITING_TIME_IN_MILLISECOND);
								packetIdToTimerMap.put(i, newTimer);
							}
							highestReceivedPacketId = packetId;
							break;
					}

					totalBytesSemaphore.acquire();
					totalBytes += nodePacket.size();
					totalBytesSemaphore.release();
					
					byte[] data = nodePacket.getData();
					process(data);
					nodePacket.setData(data);

					packet.setData(nodePacket.serialize());	
					packet.setAddress(dstAddress);
					packet.setPort(dstPort);					
					try {
						sendSocket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}

					//Report to Master that SEND has Started
					if(!sendStarted){
						sendStarted = true;
						if(!unitTest){
							report(System.currentTimeMillis(),downStreamNodes.get(flowId), EventType.SEND_START);
						}
					}
					if (unitTest) {
						System.out.println("[Processing]" + totalBytes + " " + currentTime());
					}

					if(nodePacket.isLast()){
						finished = true;
						if (!unitTest) {
							report(System.currentTimeMillis(), downStreamNodes.get(flowId), EventType.SEND_END);
						}
					}
				}	
			} catch(Exception e){
				e.printStackTrace();
			} finally{
				clean();
			}

			if (ClusterConfig.DEBUG) {
				if(finished){
					try { // wait for enough time to let the timers add the lost packetId to lost set
						Thread.sleep(MAX_WAITING_TIME_IN_MILLISECOND * 2);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): " + "Processing node has finished simulation." );
					System.out.print("[DEBUG]ProcessingNode packet lost id:");
					for(Integer i : lostPacketIdSet){
						System.out.print(i + " ");
					}
					System.out.println();
				} else if(isKilled()){
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): " + "Processing node has been killed (not finished yet)." );
				}
			}
			stop();
		}

		private void process(byte[] data){
			byte[] array = new byte[processingMemory];
			double value = 0;
			for ( int i = 0; i< processingLoop; i++) {
				value += Math.random();
			}
		}

		private void report(long time, String destinationNodeId, EventType eventType) {

			ProcReportMessage procReportMsg = new ProcReportMessage();
			procReportMsg.setStreamId(flowId);
			procReportMsg.setTime(Utility.millisecondTimeToString(time));
			procReportMsg.setDestinationNodeId(destinationNodeId);	
			procReportMsg.setEventType(eventType);

			String fromPath = ProcessingNode.super.getNodeName() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", procReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}
		}

		public void clean() {
			if (!receiveSocket.isClosed()) {
				receiveSocket.close();
			}
			if (!sendSocket.isClosed()) {
				sendSocket.close();
			}
			flowIdToSocketMap.remove(flowId);
		}
		
		public NewArrivedPacketStatus getNewArrivedPacketStatus(HashSet<Integer> lostPacketIdSet, HashMap<Integer, Timer> packetIdToTimerMap, int packetId){
			
			if(lostPacketIdSet.contains(packetId)){
				return NewArrivedPacketStatus.LOST;
			} else if(packetIdToTimerMap.containsKey(packetId)){
				return NewArrivedPacketStatus.WAIT;
			} else{
				return NewArrivedPacketStatus.NEW;
			}
		}
		
		/**
		 * This task is a process that will add the packetId to the lost set.
		 * It can be called by the timer with a scheduled delay time for the execution
		 */
		public class AddPacketIdToLostSetTask extends TimerTask{  
			  
			private int packetId;
			private Set<Integer> set;
			public AddPacketIdToLostSetTask(int packetId, Set<Integer> set){
				this.packetId = packetId;
				this.set =set;
			}
			@Override  
			public void run() {  
				set.add(packetId);
			}  
		} 
	}
	
	public enum NewArrivedPacketStatus{
		NEW, WAIT, LOST; 
	}
}
