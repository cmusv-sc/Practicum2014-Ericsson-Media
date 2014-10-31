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
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.ProcReportMessage;
import edu.cmu.util.Utility;

public class ProcessingNode extends AbstractNode{

	private HashMap<String, DatagramSocket> streamSocketMap;

	private boolean UNIT_TEST = false;
	
	private Map<String, ReceiveProcessAndSendThread> runningMap = 
			new HashMap<String, ReceiveProcessAndSendThread>();
	
	
	public ProcessingNode() throws UnknownHostException {
		super();
		streamSocketMap = new HashMap<String, DatagramSocket>();
	}

	
	@Override
	public void executeTask(StreamSpec streamSpec) {
		
		int flowIndex = -1;
		
		for (HashMap<String, String> nodeMap : streamSpec.Flow) {
			
			flowIndex++;
			
			if (nodeMap.get("NodeId").equals(getNodeName())) {
				
				/* Open a socket for receiving data from upstream node */
				Integer port = bindAvailablePortToStream(streamSpec.StreamId);
				
				/* Get processing parameters */
				long processingLoop = Long.valueOf(nodeMap.get("ProcessingLoop"));
				int processingMemory = Integer.valueOf(nodeMap.get("ProcessingMemory"));
				
				/* Get the IP:port */
				String[] addressAndPort = nodeMap.get("ReceiverIpPort").split(":");
				
				InetAddress targetAddress = null;
				try {
					targetAddress = InetAddress.getByName(addressAndPort[0]);
					int targetPort = Integer.valueOf(addressAndPort[1]);
					receiveProcessAndSend(streamSpec.StreamId, targetAddress, targetPort, processingLoop, processingMemory);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
				
				if (nodeMap.get("UpstreamUri") != null){
					try {
						HashMap<String, String> upstreamFlow = streamSpec.Flow.get(flowIndex+1);
						upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
						msgBusClient.send("/tasks", nodeMap.get("UpstreamUri")+"/tasks", "PUT", streamSpec);
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
			DatagramSocket udpSocekt = null;
			try {
				udpSocekt = new DatagramSocket(0, super.getHostAddr());
			} catch (SocketException e) {
				e.printStackTrace();
			}
			streamSocketMap.put(streamId, udpSocekt);
			return udpSocekt.getLocalPort();
		}
	}

	/**
	 * Start to receive packets from a stream and report to the management layer
	 * @param streamId
	 * @param msgBusClient
	 */
	private void receiveProcessAndSend(String streamId,  InetAddress destAddress, int dstPort, long processingLoop, int processingMemory){
		
		ReceiveProcessAndSendThread th = new ReceiveProcessAndSendThread(streamId, destAddress, dstPort, processingLoop, processingMemory);
		runningMap.put(streamId, th);
		WarpThreadPool.executeCached(th);
		
	}

	/**
	 * For Unit Test
	 */
	public void receiveProcessAndSendTest(String streamId, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveProcessAndSendThread(streamId, destAddress, dstPort, processingLoop, processingMemory));
	}

	@Override
	public void terminateTask(StreamSpec streamSpec) {
		
		if (ClusterConfig.DEBUG) {
			System.out.println("[DEBUG]ProcessingNode.terminateTask(): Received terminate request.");
		}
	
		ReceiveProcessAndSendThread th = runningMap.get(streamSpec.StreamId);
		th.kill();
		
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
		
		ReceiveProcessAndSendThread th = runningMap.get(streamSpec.StreamId);
		while (!th.isStopped());
		th.clean();
		
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
	private class ReceiveProcessAndSendThread implements Runnable {

		private String streamId;
		private InetAddress dstAddress;
		private int dstPort;
		private long processingLoop;
		private int processingMemory;
		
		private DatagramSocket receiveSocket = null;
		private DatagramSocket sendSocket = null;
		
		
		private boolean killed = false;
		private boolean stopped = false;
		
		public ReceiveProcessAndSendThread(String streamId, InetAddress destAddress, int dstPort, long processingLoop, int processingMemory) {

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


			byte[] buf = new byte[STD_DATAGRAM_SIZE]; 
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			boolean started = false;
			long startTime = 0;
			int totalBytes = 0;

			boolean finished = false;
			
			while (!finished && !isKilled()) {
				try {
					receiveSocket.receive(packet);
					if(!started) {
						startTime = System.currentTimeMillis();
						started = true;
					}
					byte[] data = packet.getData();
					totalBytes += packet.getLength();

					process(data);

					packet.setData(data);
					packet.setAddress(dstAddress);
					packet.setPort(dstPort);					
					sendSocket.send(packet);
					
					
					if (UNIT_TEST) {
						System.out.println("[Processing] totalBytes processed " + totalBytes + " " + currentTime());
					}
					
					if (packet.getData()[0] == 0) {
						finished = true;
					}

				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}	
			
			long endTime = System.currentTimeMillis();
			
			if (!UNIT_TEST) {
				report(startTime, endTime, totalBytes);
			}
			
			if (finished) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): "
							+ "Processing node has finished simulation." );
				}
				clean();
			} else if (isKilled()) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG]ProcessingNode.ReceiveProcessAndSendThread.run(): "
							+ "Processing node has been killed (not finished yet)." );
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

		private void report(long startTime, long endTime, int totalBytes) {
			
			ProcReportMessage procReportMsg = new ProcReportMessage();
			procReportMsg.setStreamId(streamId);
			procReportMsg.setStartTime(Utility.millisecondTimeToString(startTime));
			procReportMsg.setStartTime(Utility.millisecondTimeToString(endTime));	
			
			String fromPath = ProcessingNode.super.getNodeName() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", procReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}

			System.out.println("[INFO] Processing Node finished at Stream-ID " + streamId 
					+ " Total bytes " + totalBytes + 
					" Total Time " + ((endTime - startTime) / 1000) + "(sec)");
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
	
	public void setUnitTest() {
		UNIT_TEST = true;
	}

}
