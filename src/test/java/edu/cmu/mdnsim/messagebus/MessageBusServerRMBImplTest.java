package edu.cmu.mdnsim.messagebus;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.logging.Level;

import org.junit.Before;

import com.ericsson.research.trap.utils.JDKLoggerConfig;

import us.yamb.amb.spi.AsyncResultImpl;
import us.yamb.rmb.Message;
import us.yamb.rmb.RMB;
import us.yamb.rmb.annotations.GET;
import us.yamb.rmb.builders.RMBBuilder;
import us.yamb.tmb.Broker;
import edu.cmu.mdnsim.messagebus.exception.MessageBusException;

/**
 * 
 * This junit test is to examine the methods listeners are correctly added to the server.
 * 
 * @author JeremyFu
 *
 */
public class MessageBusServerRMBImplTest {
	
	MessageBusServer server;
	
	@Before
	public void broker() throws Exception
	{
		server = new MessageBusServerRMBImpl();
		server.config();
	}
	
	@org.junit.Test
	public void testMethodListner() throws Exception {
		
		String trapCfg = "trap.transport.http.url = http://127.0.0.1:8888/_connectTrap\n"
				+ "trap.transport.websocket.wsuri = ws://127.0.0.1:8888/_connectTrapWS\n";
		
		
		RMB rootRMB = RMBBuilder.builder().seed(trapCfg).build();
		Exception e = rootRMB.connect().get();
		if (e != null) {
			throw new Exception(e);
		}
		
		TestObject obj = new TestObject();
		AsyncResultImpl<String> res;
		
		
		res = new AsyncResultImpl<String>();
		obj.resetAsyncResult(res);
		server.addMethodListener("hello", "GET", obj, "getHelloWorld");
		rootRMB.get("/mdnsim/hello").data("get hello world").send();
		assertEquals(res.get(), "get hello world");
		
		res = new AsyncResultImpl<String>();
		obj.resetAsyncResult(res);
		server.addMethodListener("hello", "PUT", obj, "putHelloWorld");
		rootRMB.put("/mdnsim/hello").data("put hello world").send();
		assertEquals(res.get(), "put hello world");
		
		res = new AsyncResultImpl<String>();
		obj.resetAsyncResult(res);
		server.addMethodListener("hello", "POST", obj, "postHelloWorld");
		rootRMB.post("/mdnsim/hello").data("post hello world").send();
		assertEquals(res.get(), "post hello world");
		
		res = new AsyncResultImpl<String>();
		obj.resetAsyncResult(res);
		server.addMethodListener("hello", "DELETE", obj, "deleteHelloWorld");
		rootRMB.delete("/mdnsim/hello").data("delete hello world").send();
		assertEquals(res.get(), "delete hello world");

	}
	
	public class TestObject {
		
		AsyncResultImpl<String> res;

		
		
		public String getHelloWorld(String msg) {
			res.completed(msg);
			return "";
		}
		
		public String putHelloWorld(String msg) {
			res.completed(msg);
			return "";
		}
		
		public String postHelloWorld(String msg) {
			res.completed(msg);
			return "";
		}
		
		public String deleteHelloWorld(String msg) {
			res.completed(msg);
			return "";
		}
		
		public void resetAsyncResult(AsyncResultImpl<String> res) {
			this.res = res;
		}
		
	}
	
}
