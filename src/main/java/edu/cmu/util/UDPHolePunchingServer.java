package edu.cmu.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
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
			} catch(IOException | ClassNotFoundException e) {
				e.printStackTrace();
				break;
			}
		}
		
		serverSocket.close();
		
	}
	
	private void work() throws IOException, ClassNotFoundException {
		DatagramPacket rcvPacket = new DatagramPacket(new byte[1024], 1024);
		serverSocket.receive(rcvPacket);
		
		byte[] bytes = rcvPacket.getData();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInput in = new ObjectInputStream(bis);
		String nodeId = (String)in.readObject();
		
		in.close();
		
		InetAddress iaddr = rcvPacket.getAddress();
		int port = rcvPacket.getPort();
		System.out.println(String.format("from node[%s]:\t%s:%d", nodeId, new String(iaddr.getHostAddress()), port));
		
		DatagramPacket sndPacket = new DatagramPacket(new byte[1024], 1024);
		
		out = new ObjectOutputStream(bos);   
		out.writeObject(new UDPInfo(iaddr.getHostAddress(), port));
		
		
		
		sndPacket.setAddress(iaddr);
		sndPacket.setPort(port);
		sndPacket.setData(bos.toByteArray());

		bos.reset();
		serverSocket.send(sndPacket);
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
		private int publicPort;

		
		public UDPInfo (String publicIP, int publicPort) {
			this.publicIP = publicIP;
			this.publicPort = publicPort;

		}
		
		public String getPublicIP() {
			return this.publicIP;
		}
		
		public int getPublicPort() {
			return this.publicPort;
		}
		

	}

	
	
	
	
}
