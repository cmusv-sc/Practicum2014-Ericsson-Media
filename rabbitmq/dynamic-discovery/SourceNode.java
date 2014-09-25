import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SourceNode{

	private int PACKAGE_LENGTH = 1024;

	public void send(String ip, int port, int packageSize, double rate){
		Thread thread = new Thread(new UDPClientThread(ip, port, packageSize, rate));
		thread.start();
	}
	
	private class UDPClientThread implements Runnable{
		private String targetIp;
		private int targetPort;
		private int packageSize;
		private double sendingRate;
		
		public UDPClientThread(String ip, int port, int packageSize, double sendingRate){
			this.targetIp = ip;
			this.targetPort = port;
			this.packageSize = packageSize;
			this.sendingRate = sendingRate;
		}
		
		public void run(){
			send();
		}
		
		public void send(){
		
				DatagramSocket socket = null;
				try {
					socket = new DatagramSocket();
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				int remainSize = packageSize;
				byte buf[] = null;
				long onePackageMillisecond = (long)(1 / sendingRate); 
				
				int packageNum = 0;
				while(remainSize > 0){
					try{
						long begin = System.currentTimeMillis();
						buf = new byte[remainSize <= PACKAGE_LENGTH? remainSize : PACKAGE_LENGTH]; 
						buf[0] = (byte) (remainSize <= PACKAGE_LENGTH? 0 : 1);
     
						DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(targetIp), targetPort);  
						socket.send(packet); 

						remainSize -= PACKAGE_LENGTH;
						packageNum++;
						
						long end = System.currentTimeMillis();
						long remainMillisecond = (onePackageMillisecond * 1000 - (end - begin)) > 0 ? (onePackageMillisecond * 1000 - (end - begin)) : 0;
						try {
							Thread.sleep(remainMillisecond);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}catch(IOException e){
							e.printStackTrace();
					}
				}
				System.out.println(packageSize + " bytes sent in " + packageNum + " packages by " + currentTime());
		}
		
		public String currentTime(){
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
			Date date = new Date();
			return dateFormat.format(date);
		}
	}
	
}