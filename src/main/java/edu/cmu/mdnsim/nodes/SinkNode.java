package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.util.Utility;

public class SinkNode extends AbstractNode {
	private HashMap<String, DatagramSocket> streamSocketMap;	
	
	public SinkNode() throws UnknownHostException {
		super();
		streamSocketMap = new HashMap<String, DatagramSocket>();
	}
	
	@Override
	public void config(MessageBusClient msgBus, NodeType nType, String nName) throws MessageBusException {
		super.config(msgBus, nType, nName);
	}

	@Override
	public void executeTask(WorkConfig workConfig) {
		System.out.println("Sink received a work specification: "+JSON.toJSON(workConfig));
		int flowIndex = -1;
		for (StreamSpec streamSepc : workConfig.getStreamSpecList()) {
			for (HashMap<String, String> currentFlow : streamSepc.Flow) {
				flowIndex++;
				if (currentFlow.get("NodeId").equals(getNodeName())) {
					Integer port = bindAvailablePortToStream(streamSepc.StreamId);
					receiveAndReport(streamSepc.StreamId);
					
					if (flowIndex+1 < streamSepc.Flow.size()) {
						HashMap<String, String> upstreamFlow = streamSepc.Flow.get(flowIndex+1);
						upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
						try {
							msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", workConfig);
						} catch (MessageBusException e) {
							e.printStackTrace();
						}
					}
					break;
				}
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
	
	/**
	 * Start to receive packets from a stream and report to the management layer
	 * @param streamId
	 * @param msgBusClient
	 */
	private void receiveAndReport(String streamId){
		WarpThreadPool.executeCached(new ReceiveThread(streamId));
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
		
		public ReceiveThread(String streamId) {
			this.streamId = streamId;
		}
		
		@Override
		public void run() {				
			boolean started = false;
			long startTime = 0;
			int totalBytes = 0;
			
			DatagramSocket socket = null;
			if ((socket = streamSocketMap.get(streamId)) == null) {
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] SinkNode.ReceiveDataThread.run():" + "[Exception]Attempt to receive data for non existent stream");
				}
				return;
			}
			
			byte[] buf = new byte[STD_DATAGRAM_SIZE]; 
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			
			while (true) {
				try {	
					socket.receive(packet);
					if(!started) {
						startTime = System.currentTimeMillis();
						started = true;
					}
					totalBytes += packet.getLength();	
					System.out.println("[Sink] " + totalBytes + " bytes received at " + currentTime());		
					
					if (packet.getData()[0] == 0) {
						long endTime= System.currentTimeMillis();
						//report(startTime, endTime, totalBytes);
						socket.close();
						streamSocketMap.remove(streamId);		
						break;
					}	
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}	
			System.out.println("[Sink] finish receiving" );
		}
		
		private void report(long startTime, long endTime, int totalBytes){

			SinkReportMessage sinkReportMsg = new SinkReportMessage();
			sinkReportMsg.setStreamId(streamId);
			sinkReportMsg.setTotalBytes(totalBytes);
			sinkReportMsg.setEndTime(Utility.millisecondTimeToString(endTime));
			
			String fromPath = SinkNode.super.getNodeName() + "/finish-rcv";
			
			try {
				System.out.println("Sink finished receiving data...");
				msgBusClient.sendToMaster(fromPath, "/sink_report", "POST", sinkReportMsg);
			} catch (MessageBusException e) {
				//TODO: add exception handler
				e.printStackTrace();
			}
			
			System.out.println("[INFO] SinkNode.ReceiveDataThread.run(): " + "Sink finished receiving data at Stream-ID "+sinkReportMsg.getStreamId()+
					" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+ (endTime - startTime));
		}
	}
}
