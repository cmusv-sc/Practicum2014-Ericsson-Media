package edu.cmu.nodes;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import edu.cmu.messagebus.MdnMsgBusWarpSource;
import edu.cmu.messagebus.message.SourceReportMessage;

public class MdnSourceNode extends MdnAbstractNode {


	/**
	 * Transfers bytesToTransfer number of bytes at rate kb/second 
	 * to destination with address destAddr and port destPort with streamId 
	 * as identifier
	 * @param streamId
	 * @param destAddr
	 * @param destPort
	 * @param bytesToTransfer
	 * @param rate - kb/sec
	 * @param mdnSource
	 */
	public void sendAndReport(String streamId, InetAddress destAddr, 
			int destPort, int bytesToTransfer, int rate, 
			MdnMsgBusWarpSource mdnSource) {
		
		final long MILLIS_IN_SECOND = 1000;
		long millisRemaining = MILLIS_IN_SECOND;
		int bytesThisSecond = 0;
		int totalBytesTransferred = 0;
//		if (rate % STD_DATAGRAM_SIZE != 0) {
//			System.out.println("rate must be a multiple of 1024"
//					+ "(the standard datagram size)");
//			return;
//		}
		
		byte[] buf = new byte[STD_DATAGRAM_SIZE];
		DatagramSocket sourceSocket = null;
		InetAddress laddr;
		try {
			laddr = InetAddress.getByName(hostAddr);
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
		srcReportMsg.setStreamId(streamId);
		srcReportMsg.setTotalBytes_transferred(bytesToTransfer);
		srcReportMsg.setStartTime(this.currentTime());
		mdnSource.sourceReport(srcReportMsg);
		
		while (bytesToTransfer > 0) {
			long begin = System.currentTimeMillis();
			
			buf = new byte[bytesToTransfer <= STD_DATAGRAM_SIZE ? 
					bytesToTransfer : STD_DATAGRAM_SIZE];
			// 0 indicates the end of transmission marker
			buf[0] = (byte) (bytesToTransfer <= STD_DATAGRAM_SIZE ? 0 : 1);
			
			DatagramPacket packet = new DatagramPacket(buf, buf.length, 
					destAddr, destPort);
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

	} // sendAndReport
}
