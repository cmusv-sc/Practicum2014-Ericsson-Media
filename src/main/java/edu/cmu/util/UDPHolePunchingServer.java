package edu.cmu.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPHolePunchingServer implements Runnable{
	
	DatagramSocket serverSocket = null;
	ByteArrayOutputStream bos = new ByteArrayOutputStream();
	ObjectOutput out = null;
	
	
	public UDPHolePunchingServer(int serverPort) throws SocketException {
		serverSocket = new DatagramSocket(serverPort);
	}
	
	
	@Override
	public void run() {
		
		
		while(true) {
			try {
				work();
			} catch(IOException e) {
				e.printStackTrace();
				break;
			}
		}
		
		serverSocket.close();
		
	}
	
	private void work() throws IOException {
		DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
		serverSocket.receive(packet);
		InetAddress iaddr = packet.getAddress();
		int port = packet.getPort();
		
		out = new ObjectOutputStream(bos);   
		out.writeObject(new UDPInfo(iaddr.getHostAddress(), port));
		System.out.println("FROM " + new String(packet.getAddress().getHostAddress()) + ":" + packet.getPort());
		packet.setAddress(iaddr);
		packet.setPort(port);
		packet.setData(bos.toByteArray());
//		System.out.println(bos.toByteArray().length);
		bos.reset();
		serverSocket.send(packet);
	}
	
	
	
	public static void main(String[] args) {
		try {
			new UDPHolePunchingServer(12345).run();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static class UDPInfo implements Serializable {
		
		/**
		 * 
		 */
		private static final long serialVersionUID = -4951360107938806363L;
		private String publicIP;
//		private String localIP;
		private int publicPort;
//		private int localPort;
		
		public UDPInfo (String publicIP, int publicPort) {
			this.publicIP = publicIP;
			this.publicPort = publicPort;
//			this.localIP = localIP;
//			this.localPort = localPort;
		}
		
		public String getYourPublicIP() {
			return this.publicIP;
		}
		
		public int getYourPublicPort() {
			return this.publicPort;
		}
		
//		public String getYourLocalIP() {
//			return this.localIP;
//		}
//		
//		public int getYourLocalPort() {
//			return this.localPort;
//		}
	}

	
	
	
	
}
