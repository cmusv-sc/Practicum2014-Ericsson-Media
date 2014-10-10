package edu.cmu.mdnsim.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.mdnsim.messagebus.MessageBusClient;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.SourceReportMessage;
import edu.cmu.mdnsim.messagebus.test.WorkSpecification;

public class SourceNode extends AbstractNode {
	
	
	public SourceNode() throws UnknownHostException, MessageBusException {
		super(NodeType.SOURCE);
	}
	
	public SourceNode(String msgBusClientImplName) throws UnknownHostException, MessageBusException {
		super(msgBusClientImplName, NodeType.SOURCE);
	}
	
	@Override
	public void config() throws MessageBusException {
		msgBusClient.config();
		msgBusClient.addMethodListener("/source/exec", "POST", this, "executeTask");
		
	}

	@Override
	public void connect() throws MessageBusException {
		msgBusClient.connect();
	}
	
	@Override
	public void exectueTask(WorkSpecification ws) {
		
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
			final long MILLIS_IN_SECOND = 1000;
			long millisRemaining = MILLIS_IN_SECOND;
			int bytesThisSecond = 0;
			int totalBytesTransferred = 0;
//			if (rate % STD_DATAGRAM_SIZE != 0) {
//				System.out.println("rate must be a multiple of 1024"
//						+ "(the standard datagram size)");
//				return;
//			}
			
			byte[] buf = new byte[STD_DATAGRAM_SIZE];
			DatagramSocket sourceSocket = null;
			InetAddress laddr, destAddr = null;
			try {
				laddr = InetAddress.getByName(SourceNode.this.getNodeName());
				destAddr = InetAddress.getByName(dstAddrStr);
				sourceSocket = new DatagramSocket(0, laddr);
			} catch (UnknownHostException uhe) {
				// TODO Auto-generated catch block
				uhe.printStackTrace();
			} catch (SocketException se) {
				// TODO Auto-generated catch block
				se.printStackTrace();
			}
			
			System.out.println("[INFO] SourceNode.SendDataThread.run(): "
					+ "Source will start sending data. "
					+ "Record satrt time and report to master");
			
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(streamId);
			srcReportMsg.setTotalBytes_transferred(bytesToTransfer);
			srcReportMsg.setStartTime(this.currentTime());
			
			
			try {
				String fromPath = "/" + SourceNode.this.getNodeName() + "/ready-send";
				msgBusClient.sendToMaster(fromPath, "POST", srcReportMsg);
			} catch (MessageBusException e) {
				//TODO: Add exception handler
				e.printStackTrace();
			};
			
			while (bytesToTransfer > 0) {
				long begin = System.currentTimeMillis();
				
				buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? 
						bytesToTransfer : STD_DATAGRAM_SIZE];
				// 0 indicates the end of transmission marker
				buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);
				
				DatagramPacket packet = new DatagramPacket(buf, buf.length, 
						destAddr, dstPort);
				try {
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					// TODO Auto-generated catch block
					ioe.printStackTrace();
				}
				bytesToTransfer -= packet.getLength();
				totalBytesTransferred += packet.getLength();
				bytesThisSecond += packet.getLength();
				
				long end = System.currentTimeMillis();
				millisRemaining = MILLIS_IN_SECOND - (end - begin);
				
				if (bytesThisSecond >= (rate*STD_DATAGRAM_SIZE)) {
					bytesThisSecond = 0;
					try {
						Thread.sleep(millisRemaining);
						millisRemaining = MILLIS_IN_SECOND;
					} catch (InterruptedException ie) {
						// TODO Auto-generated catch block
						ie.printStackTrace();
					}
				} // while (bytesThisSecond >= rate)
				
			} // while(bytesToTransfer > 0)
			
			// cleanup resources
			sourceSocket.close();
			
		}
		
		private String currentTime(){
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
			Date date = new Date();
			return dateFormat.format(date);
		}
		
	}

	
}
