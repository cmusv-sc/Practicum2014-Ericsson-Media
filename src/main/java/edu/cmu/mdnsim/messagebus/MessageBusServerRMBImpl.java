package edu.cmu.mdnsim.messagebus;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.yamb.mb.util.JSON;
import us.yamb.rmb.RMB;
import us.yamb.rmb.builders.RMBBuilder;
import us.yamb.rmb.impl.RMBImpl;
import us.yamb.rmb.impl.ReflectionListener;
import us.yamb.tmb.Broker;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.JDKLoggerConfig;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.mdnsim.server.WebClient;

/**
 * 
 * This is the {@link RMB} implementation of {@link MessageBusServer}.
 * 
 * @author Geng Fu
 *
 */
public class MessageBusServerRMBImpl implements MessageBusServer {

	private Broker broker;
	private RMB rmb;
	
	static Logger logger = LoggerFactory.getLogger("embedded.mdn-manager.master");
	
	public void config() throws MessageBusException {
		
		JDKLoggerConfig.initForPrefixes(Level.FINE, "embedded");
		JDKLoggerConfig.initForPrefixes(Level.INFO, "com.ericsson");

		this.broker = new Broker();
		
		try {
			broker.listen("127.0.0.1", 8888).get();
		} catch (InterruptedException e) {
			throw new MessageBusException("Broker is threaded while waiting for broker.listen().", e);
		}
		
		System.out.println(broker.getURI());
		
		try {
			this.rmb = RMBBuilder.builder().seed(broker.getURI()).id("mdnsim").build();
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			throw new MessageBusException("Cannot instantiate RMB implementation.", e);
		}
		
		Exception exception;
		try {
			exception = rmb.connect().get();
		} catch (InterruptedException e) {
			throw new MessageBusException("RMB thread is interrupted while waiting for conect to broker.", e);
		}
		
		if (exception != null)
			throw new MessageBusException("An exception is caught while connect to RMB broker.", exception);
		
		try {
			WebClient.load(broker.getServer().getHostingTransport("http"));
		} catch (IOException e) {
			throw new MessageBusException(e);
		} catch (TrapException e) {
			throw new MessageBusException(e);
		}

		
	}

	public void send(String fromPath, String dstURI, String method,
			MbMessage msg) throws MessageBusException {
		
		if (msg == null) {
			throw new MessageBusException("MbMessage is null.");
		}
		
		
		RMB from = rmb.create(fromPath);
		if (msg != null) {
			msg.from(from.id());
		}
		
		try {
			from.message().to(dstURI).method(method).data(JSON.toJSON(msg)).send();
			logger.debug("MessageBusServerRMBImpl.send(): sends from " + from.id() + " to " + dstURI + " method: " + method + " msg " + msg);
		} catch (IOException e) {
			throw new MessageBusException(e);
		}
		
		
	}

	public void addMethodListener(String path, String method, final Object object,
			final String objectMethod) throws MessageBusException {
		
		if (path != null && path.length() > 0 && path.charAt(0) == '/') {
			path = path.substring(1);
		}
		
		Method m = MessageBusUtil.parseMethod(object, objectMethod);
		ReflectionListener listener = new ReflectionListener((RMBImpl) rmb, object, m, path);
		
		Field field = null;
		RMB rmb;
		try {
			field = MessageBusUtil.getField(listener, "rmb");
			field.setAccessible(true);
			rmb = (RMB) field.get(listener);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new MessageBusException("Cannot find rmb filed in ReflectionListener", e);
		}
		
		
		method = method.trim().toUpperCase();
		if (method.equals("GET")) {
			rmb.onget(message -> listener.receiveMessage(message));
		} else if (method.equals("PUT")) {
			rmb.onput(message -> listener.receiveMessage(message));
		} else if (method.equals("POST")) {
			rmb.onpost(message -> listener.receiveMessage(message));
		} else if (method.equals("DELETE")) {
			rmb.ondelete(message -> listener.receiveMessage(message));
		}
		
	}

	
	public String getURL() {
		return rmb.id();
	}
	

}
