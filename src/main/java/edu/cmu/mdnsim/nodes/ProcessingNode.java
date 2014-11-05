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

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.exception.TerminateTaskBeforeExecutingException;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.EventType;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.util.Utility;

public class ProcessingNode extends AbstractNode{

	private Map<String, ReceiveProcessAndSendRunnable> runningMap = new HashMap<String, ReceiveProcessAndSendRunnable>();

	public ProcessingNode() throws UnknownHostException {
		super();
	}


	@Override
	public void executeTask(StreamSpec streamSpec) {

		int flowIndex = -1;

		for (HashMap<String, String> nodePropertiesMap : streamSpec.Flow) {

			flowIndex++;

			if (nodePropertiesMap.get("NodeId").equals(getNodeName())) {

				/* Open a socket for receiving data from upstream node */
				int port = bindAvailablePortToStream(streamSpec.StreamId);
				if(port == 0){
					//TODO, report to the management layer, we failed to bind a port to a socket
				}

				/* Get processing parameters */
				long processingLoop = Long.valueOf(nodePropertiesMap.get("ProcessingLoop"));
				int processingMemory = Integer.valueOf(nodePropertiesMap.get("ProcessingMemory"));

				//Get up stream and down stream node ids
				upStreamNodes.put(streamSpec.StreamId, nodePropertiesMap.get("UpstreamId"));
				downStreamNodes.put(streamSpec.StreamId, nodePropertiesMap.get("DownstreamId"));

				/* Get the IP:port */
				String[] addressAndPort = nodePropertiesMap.get("ReceiverIpPort").split(":");

				InetAddress targetAddress = null;
				try {
					targetAddress = InetAddress.getByName(addressAndPort[0]);
					int targetPort = Integer.valueOf(addressAndPort[1]);
					ReceiveProcessAndSendRunnable thread = new ReceiveProcessAndSendRunnable(streamSpec.StreamId, targetAddress, targetPort, processingLoop, processingMemory);
					runningMap.put(streamSpec.StreamId, thread);
					WarpThreadPool.executeCached(thread);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}

				if (nodePropertiesMap.get("UpstreamUri") != null){
					try {
						HashMap<String, String> upstreamFlow = streamSpec.Flow.get(flowIndex+1);
						upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port);
						msgBusClient.send("/tasks", nodePropertiesMap.get("UpstreamUri")+"/tasks", "PUT", streamSpec);
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
	public void receiveProcessAndSendTest(String streamId, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveProcessAndSendRunnable(streamId, destAddress, dstPort, processingLoop, processingMemory));
	}

	@Override
	public void terminateTask(StreamSpec streamSpec) {

		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received terminate request.");
		}

		ReceiveProcessAndSendRunnable thread = runningMap.get(streamSpec.StreamId);
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
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received clean resource request.");
		}

		ReceiveProcessAndSendRunnable thread = runningMap.get(streamSpec.StreamId);
		while (!thread.isStopped());
		thread.clean();

		Map<String, String> nodeMap = streamSpec.findNodeMap(getNodeName());
		try {
			msgBusClient.send("/tasks", nodeMap.get("DownstreamUri") + "/tasks", "DELETE", streamSpec);
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
	 * @param streamId The streamId is bind to a socket and stored in the map
	 * @param msgBus The message bus used to report to the master
	 * 
	 */
	private class ReceiveProcessAndSendRunnable implements Runnable {

		private String streamId;
		
		private DatagramSocket receiveSocket;
		
		private long processingLoop;
		private int processingMemory;
		private InetAddress dstAddress;
		private int dstPort;
		private DatagramSocket sendSocket;

		private boolean killed = false;
		private boolean stopped = false;

		public ReceiveProcessAndSendRunnable(String streamId, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory) {

			this.streamId = streamId;
			this.dstAddress = destAddress;
			this.dstPort = dstPort;
			this.processingLoop = processingLoop;
			this.processingMemory = processingMemory;
		}

		@Override
		public void run() {

			if ((receiveSocket = streamSocketMap.get(streamId)) == null) {
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

			boolean receiveStarted = false;
			boolean sendStarted = false;
			long startTime = 0;
			int totalBytesTransported = 0;

			boolean finished = false;
			try{
				while (!finished && !isKilled()) {
					try {
						receiveSocket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					if(!receiveStarted) {
						startTime = System.currentTimeMillis();
						receiveStarted = true;
						//Report to Master that RECEIVE has Started
						if(!unitTest){
							report(startTime,upStreamNodes.get(streamId),EventType.RECEIVE_START);
						}
					}
					
					byte[] rawData = packet.getData();
					NodePacket nodePacket = new NodePacket(rawData);					
					totalBytesTransported += nodePacket.size();
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
							report(System.currentTimeMillis(),downStreamNodes.get(streamId), EventType.SEND_START);
						}
					}
					if (unitTest) {
						System.out.println("[Processing]" + totalBytesTransported + " " + currentTime());
					}
	
					if(nodePacket.isLast()){
						finished = true;
					}
				}	
			} catch(Exception e){
				e.printStackTrace();
			} finally{
				clean();
			}

			long endTime = System.currentTimeMillis();

			if (!unitTest) {
				//TODO: How to figure out that RECEIVE has ended?
				//Report to Master that SEND has Ended
				report(endTime, downStreamNodes.get(streamId), EventType.SEND_END);
			}

			if (ClusterConfig.DEBUG) {
				if(finished){
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): " + "Processing node has finished simulation." );
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
			procReportMsg.setStreamId(streamId);
			procReportMsg.setTime(Utility.millisecondTimeToString(time));
			procReportMsg.setDestinationNodeId(destinationNodeId);	
			procReportMsg.setEventType(eventType);
			
			String fromPath = ProcessingNode.super.getNodeName() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", procReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}

//			System.out.println("[INFO] Processing Node finished at Stream-ID " + streamId 
//					+ " Total bytes " + totalBytes + 
//					" Total Time " + ((endTime - startTime) / 1000) + "(sec)");
		}

		public synchronized void kill() {
			killed = true;
		}

		public synchronized boolean isKilled() {
			return killed;
		}

		public synchronized void stop() {
			stopped = true;
		}

		public synchronized boolean isStopped() {
			return stopped;
		}

		public void clean() {
			if (!receiveSocket.isClosed()) {
				receiveSocket.close();
			}
			if (!sendSocket.isClosed()) {
				sendSocket.close();
			}
			streamSocketMap.remove(streamId);
		}
	}
}
