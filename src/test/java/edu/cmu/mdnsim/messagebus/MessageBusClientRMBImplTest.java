package edu.cmu.mdnsim.messagebus;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;

import us.yamb.amb.spi.AsyncResultImpl;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

public class MessageBusClientRMBImplTest {
	
	/**
	 * This test examines the connectivity between client with master, and the
	 * {@link MessageBusClient.sendToMaster()} method.
	 * 
	 * @throws Exception
	 */
	
	static MessageBusServer server;
	
	@BeforeClass
	public static void broker() throws Exception
	{
		if (server == null) {
			server = new MessageBusServerRMBImpl();
			server.config();
		}
	}
	
	@org.junit.Test
	public void testConnectivity() throws Exception {
		
		TestObject obj = new TestObject();
		
		server.addMethodListener("hello", "GET", obj , "helloMethod");
		
		MessageBusClient client = new MessageBusClientRMBImpl("127.0.0.1");
		client.config();
		client.connect();
		
		String data = "Greetings from test client";
		client.sendToMaster("/hello", "/hello", "GET", new TestMessage(data));
		assertEquals(obj.getResult(), data);
		
	}
	
	@org.junit.Test
	public void testAddMethodListner() throws Exception {
		
		
		
		MessageBusClient client = new MessageBusClientRMBImpl("127.0.0.1");
		client.config();
		client.connect();
		
		String data = "Greetings from test client";
		
		TestObject obj = null;
		
		obj = new TestObject();
		client.addMethodListener("/hello", "GET", obj, "helloMethod");
		server.send("/master", client.getURI() + "/hello", "GET", new TestMessage(data));
		assertEquals(obj.getResult(), data);
		
		obj = new TestObject();
		client.addMethodListener("/hello", "PUT", obj, "helloMethod");
		server.send("/master", client.getURI() + "/hello", "PUT", new TestMessage(data));
		assertEquals(obj.getResult(), data);
		
		obj = new TestObject();
		client.addMethodListener("/hello", "DELETE", obj, "helloMethod");
		server.send("/master", client.getURI() + "/hello", "DELETE", new TestMessage(data));
		assertEquals(obj.getResult(), data);
		
		obj = new TestObject();
		client.addMethodListener("/hello", "POST", obj, "helloMethod");
		server.send("/master", client.getURI() + "/hello", "POST", new TestMessage(data));
		assertEquals(obj.getResult(), data);
	}
	
	
	
	
	public static class TestMessage extends MbMessage {
		
		private String content;
		
		TestMessage() {
			content = "default";
		}
		
		TestMessage(String content) {
			this.content = content;
		}
		
		void setContent(String content) {
			this.content = content;
		}
		
		String getContent() {
			return content;
		}
		
	}
	
	
	public static class TestObject {
		
		private AsyncResultImpl<String> res = new AsyncResultImpl<String>();
		
		public void helloMethod(TestMessage msg) {
			res.completed(msg.getContent());
		}
		
		public String getResult() throws InterruptedException{
			return res.get();
		}
	}
	
}
