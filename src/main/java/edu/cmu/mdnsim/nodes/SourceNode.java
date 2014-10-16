package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;
import edu.cmu.util.Utility;

public class SourceNode extends AbstractNode {
	
	/**
	 * Uses the default MessageBusClientWarpImpl as the message bus implementation
	 * @throws UnknownHostException
	 * @throws MessageBusException
	 */
	public SourceNode() throws UnknownHostException, MessageBusException {
		super(NodeType.SOURCE);
	}
	
	public SourceNode(String msgBusClientImplName) throws UnknownHostException, MessageBusException {
		super(msgBusClientImplName, NodeType.SOURCE);
	}
	
	@Override
	public void config() throws MessageBusException {
		msgBusClient.config();
		msgBusClient.addMethodListener("/tasks", "PUT", this, "executeTask");
	}

	
	@Override
	public void executeTask(WorkSpecification ws) {
		
		Map<String, Object> dsConfig = ws.getDownstreamConfig();
		
		sendAndReport((String)dsConfig.get("stream-id"), 
				(String)dsConfig.get("dst-ip"), (Integer)dsConfig.get("dst-port"),
				(Integer)dsConfig.get("data-size"), (Integer)dsConfig.get("data-rate"), 
				super.msgBusClient);
	}
	
	/**
	 * 
	 * Starts to send data to specified destination.
	 * 
	 * @param streamId The stream id associated with this stream
	 * @param destAddrStr The destination address
	 * @param destPort The destination port
	 * @param bytesToTransfer Total bytes to be sent
	 * @param rate The rate to send data
	 * @param msgBusClient The message bus client to send message
	 * 
	 */
	private void sendAndReport(String streamId, String destAddrStr, 
			int destPort, int bytesToTransfer, int rate, 
			MessageBusClient msgBusClient) {
		
		WarpThreadPool.executeCached(new SendDataThread(streamId, destAddrStr, 
			destPort, bytesToTransfer, rate, msgBusClient));

	}
	
	private class SendDataThread implements Runnable {
		
		private String streamId;
		private String dstAddrStr;
		private int dstPort;
		private int bytesToTransfer;
		private int rate;
		private MessageBusClient msgBusClient;
		
		public SendDataThread(String streamId, String dstAddrStr, 
				int dstPort, int bytesToTransfer, int rate, 
				MessageBusClient msgBusClient) {
			
			SendDataThread.this.streamId = streamId;
			SendDataThread.this.dstAddrStr = dstAddrStr;
			SendDataThread.this.dstPort = dstPort;
			SendDataThread.this.bytesToTransfer = bytesToTransfer;
			SendDataThread.this.rate = rate;
			SendDataThread.this.msgBusClient = msgBusClient;
			
		}
		
		@Override
		public void run() {
			double packetPerSecond = rate / STD_DATAGRAM_SIZE;
			long millisecondPerPacket = (long)(1 / packetPerSecond) * 1000; 
			
			DatagramSocket sourceSocket = null;
			InetAddress laddr = null;
			try {
				laddr = InetAddress.getByName(SourceNode.this.getNodeName());
				sourceSocket = new DatagramSocket(0, laddr);
			} catch (UnknownHostException uhe) {
				uhe.printStackTrace();
			} catch (SocketException se) {
				se.printStackTrace();
			}
			
			System.out.println("[INFO] SourceNode.SendDataThread.run(): " + "Source will start sending data. "
					+ "Record satrt time and report to master");
			
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(streamId);
			srcReportMsg.setTotalBytes_transferred(bytesToTransfer);
			srcReportMsg.setStartTime(Utility.currentTime());	
			String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
			try {
				msgBusClient.sendToMaster(fromPath, "reports", "POST", srcReportMsg);
			} catch (MessageBusException e) {
				e.printStackTrace();
			};
			
			byte[] buf = null;
			while (bytesToTransfer > 0) {
				long begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? bytesToTransfer : STD_DATAGRAM_SIZE];
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);			
				DatagramPacket packet = null;
				try {
					packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(dstAddrStr), dstPort);
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				
				long end = System.currentTimeMillis();
				long millisRemaining = millisecondPerPacket - (end - begin);
				if (millisRemaining > 0) {
					try {
						Thread.sleep(millisRemaining);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
			} 
			sourceSocket.close();
		}
	}

	
}
