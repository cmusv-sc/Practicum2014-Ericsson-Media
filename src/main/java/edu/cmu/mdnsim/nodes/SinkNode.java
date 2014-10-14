package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.global.ClusterConfig;
import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeReply;
import edu.cmu.mdnsim.messagebus.message.RegisterNodeRequest;
import edu.cmu.mdnsim.messagebus.message.SinkReportMessage;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;

public class SinkNode extends AbstractNode {
	
	private HashMap<String, DatagramSocket> _streamSocketMap;
	
	
	public SinkNode() throws UnknownHostException, MessageBusException {
		this("edu.cmu.mdnsim.messagebus.MessageBusClientWarpImpl");
	}
	
	public SinkNode(String msgBusClientImplName) throws UnknownHostException, MessageBusException {
		super(msgBusClientImplName, NodeType.SINK);
		_streamSocketMap = new HashMap<String, DatagramSocket>(); 
	}
	
	@Override
	public void config() throws MessageBusException {
		msgBusClient.config();
		msgBusClient.addMethodListener("/tasks", "PUT", this, "executeTask");
		
	}
	
	

	@Override
	public void executeTask(WorkSpecification ws) {
		
		Map<String, Object> config = ws.getConfig();
		int port = bindAvailablePortToStream((String)config.get("stream-id"));
		WarpThreadPool.executeCached(new ReceiveDataThread((String)config.get("stream-id"), msgBusClient));
		
		ws.setReceiverIpAndPort(super.getHostAddr().getHostAddress(), port);
		String fromPath = "/" + super.getNodeName() + "/prep";
		
		try {
			// Increment the work specification index to next node
			ws.incrementNodeIdx();
			msgBusClient.send(fromPath, ws.getNextNodeURI(), "POST", ws);
		} catch (MessageBusException e) {
			//TODO: add exception handler
			e.printStackTrace();
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

		
		if (_streamSocketMap.containsKey(streamId)) {
			// TODO handle potential error condition. We may consider throw this exception
			if (ClusterConfig.DEBUG) {
				System.out.println("[DEBUG] SinkeNode.bindAvailablePortToStream():"
						+ "[Exception]Attempt to add a socket mapping to existing stream!");
			}
			return _streamSocketMap.get(streamId).getPort();
		} else {
			_streamSocketMap.put(streamId, udpSocekt);
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
			long startTime = (long) 0;
			long totalTime = (long) 0;
			int totalBytes = 0;
			
			DatagramSocket socket;
			if ((socket = _streamSocketMap.get(streamId)) == null) {
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
						sinkReportMsg.setEndTime(this.currentTime());
						//sinkReportMsg.setTotalTime(totalTime);
						
						String fromPath = SinkNode.super.getNodeName() + "/finish-rcv";
						
						try {
							msgBusClient.sendToMaster(fromPath, "reports", "POST", sinkReportMsg);
						} catch (MessageBusException e) {
							//TODO: add exception handler
							e.printStackTrace();
						}
						
						
						System.out.println("[INFO] SinkNode.ReceiveDataThread.run(): "
								+ "Sink finished receiving data at Stream-ID "+sinkReportMsg.getStreamId()+
								" Total bytes "+sinkReportMsg.getTotalBytes()+ " Total Time "+totalTime);
						// cleanup resources
						socket.close();
						_streamSocketMap.remove(streamId);
						
						break;
					}
					
				} catch (IOException ioe) {
					// TODO Handle error condition
					ioe.printStackTrace();
				}
			}
			
		}
		
		private String currentTime() {
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
			Date date = new Date();
			return dateFormat.format(date);
		}
	}



}
