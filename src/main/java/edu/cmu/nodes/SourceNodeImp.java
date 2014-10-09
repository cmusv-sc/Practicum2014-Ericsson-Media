package edu.cmu.nodes;

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

import com.ericsson.research.warp.util.WarpThreadPool;

import edu.cmu.messagebus.NodeContainer;
import edu.cmu.messagebus.message.SourceReportMessage;

public class SourceNodeImp extends MdnAbstractNode implements SourceNode {

	
	@Override
	public void sendAndReport(String streamId, String destAddrStr, 
			int destPort, int bytesToTransfer, int rate, 
			NodeContainer mdnSource) {
		
		WarpThreadPool.executeCached(new SendDataThread(streamId, destAddrStr, 
			destPort, bytesToTransfer, rate, mdnSource));

	} // sendAndReport
	
	private class SendDataThread implements Runnable {
		
		private String _streamId;
		private String _dstAddrStr;
		private int _dstPort;
		private int _bytesToTransfer;
		private int _rate;
		private NodeContainer _nodeContainer;
		
		public SendDataThread(String streamId, String dstAddrStr, 
				int dstPort, int bytesToTransfer, int rate, 
				NodeContainer nodeContainer) {
			_streamId = streamId;
			_dstAddrStr = dstAddrStr;
			_dstPort = dstPort;
			_bytesToTransfer = bytesToTransfer;
			_rate = rate;
			_nodeContainer = nodeContainer;
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
				laddr = InetAddress.getByName(hostAddr);
				destAddr = InetAddress.getByName(_dstAddrStr);
				sourceSocket = new DatagramSocket(0, laddr);
			} catch (UnknownHostException uhe) {
				// TODO Auto-generated catch block
				uhe.printStackTrace();
			} catch (SocketException se) {
				// TODO Auto-generated catch block
				se.printStackTrace();
			}
			if (sourceSocket == null) 
				return;
			
			System.out.println("Source will start sending data. "
					+ "Record satrt time and report to master");
			SourceReportMessage srcReportMsg = new SourceReportMessage();
			srcReportMsg.setStreamId(_streamId);
			srcReportMsg.setTotalBytes_transferred(_bytesToTransfer);
			srcReportMsg.setStartTime(this.currentTime());
			_nodeContainer.sourceReport(srcReportMsg);
			
			while (_bytesToTransfer > 0) {
				long begin = System.currentTimeMillis();
				
				buf = new byte[_bytesToTransfer <= STD_DATAGRAM_SIZE ? 
						_bytesToTransfer : STD_DATAGRAM_SIZE];
				// 0 indicates the end of transmission marker
				buf[0] = (byte) (_bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);
				
				DatagramPacket packet = new DatagramPacket(buf, buf.length, 
						destAddr, _dstPort);
				try {
					sourceSocket.send(packet);
				} catch (IOException ioe) {
					// TODO Auto-generated catch block
					ioe.printStackTrace();
				}
				_bytesToTransfer -= packet.getLength();
				totalBytesTransferred += packet.getLength();
				bytesThisSecond += packet.getLength();
				
				long end = System.currentTimeMillis();
				millisRemaining = MILLIS_IN_SECOND - (end - begin);
				
				if (bytesThisSecond >= (_rate*STD_DATAGRAM_SIZE)) {
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
