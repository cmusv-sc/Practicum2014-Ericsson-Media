package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.ericsson.research.warp.util.JSON;
import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.config.StreamSpec;
import edu.cmu.mdnsim.config.WorkConfig;
import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;
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
	public void executeTask(StreamSpec s) {
		System.out.println("Sink received a stream specification: "+JSON.toJSON(s));
		int flowIndex = -1;
		for (HashMap<String, String> currentFlow : s.Flow) {
			flowIndex++;
			if (!currentFlow.get("NodeId").equals(getNodeName())) {
				continue;
			}
			else {
				//System.out.println("FOUND ME!! "+currentFlow.get("NodeId"));
				Integer port = bindAvailablePortToStream(s.StreamId);
				WarpThreadPool.executeCached(new ReceiveDataThread(s.StreamId, msgBusClient));

				if (flowIndex+1 < s.Flow.size()) {
					HashMap<String, String> upstreamFlow = s.Flow.get(flowIndex+1);
					upstreamFlow.put("ReceiverIpPort", super.getHostAddr().getHostAddress()+":"+port.toString());
					try {
						msgBusClient.send("/tasks", currentFlow.get("UpstreamUri")+"/tasks", "PUT", s);
					} catch (MessageBusException e) {
						//TODO: add exception handler
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

		DatagramSocket udpSocekt = null;
		try {
			udpSocekt = new DatagramSocket(0, super.getHostAddr());
		} catch (SocketException e) {
			//TODO: Handle the exception. We may consider throw this exception
			e.printStackTrace();
		}

		
		if (streamSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():"
						+ "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return streamSocketMap.get(streamId).getPort();
		} else {
			streamSocketMap.put(streamId, udpSocekt);
			return udpSocekt.getLocalPort();
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
	private class ReceiveDataThread implements Runnable {
		
		private String streamId;
		
		public ReceiveDataThread(String streamId, MessageBusClient msgBusClient) {
			ReceiveDataThread.this.streamId = streamId;
		}
		
		@Override
		public void run() {
			
			boolean started = false;
			long startTime = 0;
			long totalTime = 0;
			int totalBytes = 0;
			
			DatagramSocket socket = null;
			if ((socket = streamSocketMap.get(streamId)) == null) {
				// TODO handle potential error condition
				if (ClusterConfig.DEBUG) {
					System.out.println("[DEBUG] SinkNode.ReceiveDataThread.run():"
							+ "[Exception]Attempt to receive data for non existent stream");
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
					
					if (packet.getData()[0] == 0) {
						totalTime = System.currentTimeMillis() - startTime;
						//TODO report the time taken and total bytes received 
						SinkReportMessage sinkReportMsg = new SinkReportMessage();
						sinkReportMsg.setStreamId(streamId);
						sinkReportMsg.setTotalBytes(totalBytes);
						sinkReportMsg.setEndTime(Utility.currentTime());
						//sinkReportMsg.setTotalTime(totalTime);
						
						String fromPath = SinkNode.super.getNodeName() + "/finish-rcv";
						
						try {
							System.out.println("Sink finished receiving data...");
							msgBusClient.sendToMaster(fromPath, "/sink_report", "POST", sinkReportMsg);
						} catch (MessageBusException e) {
							//TODO: add exception handler
							e.printStackTrace();
						}
						
						
						System.out.println("[INFO] SinkNode.ReceiveDataThread.run(): "
								+ "Sink finished receiving data at Stream-ID "+sinkReportMsg.getStreamId()+
								" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+totalTime);
						// cleanup resources
						socket.close();
						streamSocketMap.remove(streamId);
						
						break;
					}
					
				} catch (IOException ioe) {
					// TODO Handle error condition
					ioe.printStackTrace();
				}
			}
			
		}
	}
}
