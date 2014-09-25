import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SinkNode{
	
	private int PACKAGE_LENGTH = 1024;
		
	public void listenAtPort(int port){
		UDPServerThread udpServerThread = new UDPServerThread(port);
		Thread thread = new Thread(udpServerThread);
		thread.start();
	}	
	
	private class UDPServerThread implements Runnable{
		private int port;
		
		public UDPServerThread(int port){
			this.port = port;
		}
		
		public void run(){
			constantlyListen();
		}
		
		public void constantlyListen(){
			DatagramSocket socket = null;
			byte buf[] = new byte[PACKAGE_LENGTH]; 
			try {
				socket = new DatagramSocket(port);
			} catch (SocketException e1) {
				e1.printStackTrace();
			}  
			
			int packageSizeSum = 0;
			int packageNum = 0;
			while(true){
				try{	 
					DatagramPacket packet = new DatagramPacket(buf, buf.length);  
					socket.receive(packet);
					packageSizeSum += packet.getLength();
					packageNum++;
		  
					System.out.println(packageSizeSum + " bytes received in " + packageNum + " packages by " + currentTime());			
					if(packet.getData()[0] == 0){
						break;
					}
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}		
		}	
		
		public String currentTime(){
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
			Date date = new Date();
			return dateFormat.format(date);
		}
	}
}
