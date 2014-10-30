package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.util.Utility;

public class ProcessingNode extends AbstractNode{

	private HashMap<String, DatagramSocket> streamSocketMap;
	private long processingLoop;
	private int spaceConsumption;

	public ProcessingNode(long processingLoop, int spaceConsumption) throws UnknownHostException {
		super();
		this.processingLoop = processingLoop;
		this.spaceConsumption = spaceConsumption;
		streamSocketMap = new HashMap<String, DatagramSocket>();
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
				//TODO: Handle the exception. We may consider throw this exception
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
	private void receiveProcessAndSend(String streamId,  InetAddress destAddress, int dstPort){
		WarpThreadPool.executeCached(new ReceiveProcessAndSendThread(streamId, destAddress, dstPort));
	}

	/**
	 * For Unit Test
	 */
	public void receiveProcessAndSendTest(String streamId, InetAddress destAddress, int dstPort){
		ExecutorService executorService = Executors.newCachedThreadPool();
		executorService.execute(new ReceiveProcessAndSendThread(streamId, destAddress, dstPort));
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

		public ReceiveProcessAndSendThread(String streamId, InetAddress destAddress, int dstPort) {

			this.streamId = streamId;
			this.dstAddress = destAddress;
			this.dstPort = dstPort;
		}

		@Override
		public void run() {

			DatagramSocket receiveSocket = null;
			if ((receiveSocket = streamSocketMap.get(streamId)) == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] SinkNode.ReceiveDataThread.run():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return;
			}

			DatagramSocket sendSocket = null;
			InetAddress laddr = null;
			try {
				laddr = InetAddress.getByName(ProcessingNode.this.getNodeName());
				//sendSocket = new DatagramSocket(0, laddr);
				sendSocket = new DatagramSocket();
			} catch (UnknownHostException uhe) {
				uhe.printStackTrace();
			} catch (SocketException se) {
				se.printStackTrace();
			}


			byte[] buf = new byte[STD_DATAGRAM_SIZE]; 
			DatagramPacket packet = new DatagramPacket(buf, buf.length);

			boolean started = false;
			long startTime = 0;
			int totalBytes = 0;

			while (true) {
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

					System.out.println("[Processing] totalBytes processed " + totalBytes + " " + currentTime());
					if (packet.getData()[0] == 0) {
						long endTime = System.currentTimeMillis();
						//report(startTime, endTime, totalBytes);
						streamSocketMap.remove(streamId);
						receiveSocket.close();
						sendSocket.close();						
						break;
					}

				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}		
			System.out.println("[Processing] finish receiving, processing and sending");
		}

		private void process(byte[] data){
			byte[] array = new byte[spaceConsumption];
			double value = 0;
			for ( int i = 0; i< processingLoop; i++) {
				value+=Math.random();
			}
		}

		private void report(long startTime, long endTime, int totalBytes){

			MbMessage message = new MbMessage();
			message.setStreamId(streamId);
			message.setTotalBytes(totalBytes);
			message.setEndTime(Utility.millisecondTimeToString(endTime));

			String fromPath = ProcessingNode.super.getNodeName() + "/finish-rcv";
			try {
				msgBusClient.sendToMaster(fromPath, "/processing_report", "POST", message);
			} catch (MessageBusException e) {
				e.printStackTrace();
			}

			System.out.println("[INFO] Processing Node finished at Stream-ID " + streamId + " Total bytes "+totalBytes+ " Total Time " + (endTime - startTime));
		}
	}

	@Override
	public void executeTask(StreamSpec streamSpec) {
		for (HashMap<String, String> currentFlow : streamSpec.Flow) {
			if (currentFlow.get("NodeId").equals(getNodeName())) {
				Integer port = bindAvailablePortToStream(streamSpec.StreamId);
				String[] addressAndPort = currentFlow.get("ReceiverIpPort").split(":");
				InetAddress targetAddress = null;
				try {
					targetAddress = InetAddress.getByName(addressAndPort[0]);
					int targetPort = Integer.valueOf(addressAndPort[1]);
					//TODO: Read Processing Node related parameters from user input
					receiveProcessAndSend(streamSpec.StreamId, targetAddress, targetPort);
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
				if (currentFlow.get("UpstreamUri") != null){
					try {
						msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", streamSpec);
					} catch (MessageBusException e) {
						e.printStackTrace();
					}
				}
				break;
			}
		}	
	}
}
