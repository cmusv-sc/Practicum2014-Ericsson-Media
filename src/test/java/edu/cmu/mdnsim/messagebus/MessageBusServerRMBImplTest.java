package edu.cmu.mdnsim.messagebus;

import java.io.IOException;

import us.yamb.rmb.RMB;
import us.yamb.rmb.builders.RMBBuilder;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

public class MessageBusServerRMBImplTest {
	
	@org.junit.Test
	public void testConnectivity() {
		
		MessageBusServer server = new MessageBusServerRMBImpl();
		try {
			server.config();
		} catch (MessageBusException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		TestObject obj = new TestObject();
		try {
			server.addMethodListener("/hello", "GET", obj, "hello");
		} catch (MessageBusException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		
		
		String trapCfg = "trap.transport.http.url = http://127.0.0.1:8888/_connectTrap\n"
				+ "trap.transport.websocket.wsuri = ws://127.0.0.1:8888/_connectTrapWS\n";
		
		try {
			RMB rootRMB = RMBBuilder.builder().seed(trapCfg).build();
			try {
				Exception e = rootRMB.connect().get();
				if (e != null) {
					System.out.println(e.toString());
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("rootRMB:" + rootRMB.id());
			rootRMB.message().to("/mdnsim/hello").method("GET").send();
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
			
//		System.exit(0);
				
	}
	
	
	private class TestObject {
		
		public void hello() {
			System.out.println("hello");
		}
		
	}
}
