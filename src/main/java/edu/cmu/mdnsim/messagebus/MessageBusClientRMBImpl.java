package edu.cmu.mdnsim.messagebus;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Level;

import us.yamb.mb.util.JSON;
import us.yamb.rmb.RMB;
import us.yamb.rmb.Request.Reply;
import us.yamb.rmb.builders.RMBBuilder;
import us.yamb.rmb.impl.RMBImpl;
import us.yamb.rmb.impl.ReflectionListener;

import com.ericsson.research.trap.spi.nhttp.handlers.Resource;
import com.ericsson.research.trap.utils.JDKLoggerConfig;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;

/**
 * 
 * This is the implementation of {@link MessageBusClient} by using Warp library. 
 * 
 * @author Jeremy Fu
 *
 */
public class MessageBusClientRMBImpl implements MessageBusClient {

	private final String masterIP;
	
	private RMB rootRMB;
	
	private boolean connected = false;
	
	/**
	 * The constructor of MessageBusClientWaprImpl. It takes the IP address of 
	 * Master as argument.
	 * @param ip The IP address of Master. The IP address is denoted in dotted 
	 * decimal.
	 */
	public MessageBusClientRMBImpl(String ip) {
		this.masterIP = ip;
	}

	@Override
	public void config() throws MessageBusException {
		/* Initialize the message bus */
		//JDKLoggerConfig.initForPrefixes(Level.INFO, "warp");
		JDKLoggerConfig.initForPrefixes(Level.FINE, "embedded");

		try {
			String trapCfg = String.format("trap.transport.http.url = http://%s:8888/_connectTrap\n"
					+ "trap.transport.websocket.wsuri = ws://%s:8888/_connectTrapWS\n", masterIP, masterIP);
			this.rootRMB = RMBBuilder.builder().seed(trapCfg).build();
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			throw new MessageBusException("Cannot instantiate RMB implementation.", e);
		}
		
		
	}
	
	@Override
	public synchronized void connect() throws MessageBusException {
		
		Exception exception;
		try {
			exception = rootRMB.connect().get();
		} catch (InterruptedException e) {
			throw new MessageBusException("RMB thread is interrupted while waiting for conect to broker.", e);
		}
		
		if (exception != null)
			throw new MessageBusException("An exception is caught while connect to RMB broker.", exception);
		
		connected = true;
		notifyAll();
		
	}
	

	@Override
	public void addMethodListener(String path, String method, final Object object,
			final String objectMethod) throws MessageBusException {
		
		if (path != null && path.length() > 0 && path.charAt(0) == '/') {
			path = path.substring(1);
		}
		
		Method m = MessageBusUtil.parseMethod(object, objectMethod);
		ReflectionListener listener = new ReflectionListener((RMBImpl) rootRMB, object, m, path);
		
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


	@Override
	public void send(String fromPath, String dstURI, String method, MbMessage msg)
			throws MessageBusException {
		
		RMB from = rootRMB.create(fromPath);
		try {
			msg.from(from.id());
			msg.to(dstURI);
			from.message().to(dstURI).data(JSON.toJSON(msg)).method(method).send();
		} catch (IOException e) {
			throw new MessageBusException("Failed to send data.", e);
		}
		
	}



	@Override
	public void sendToMaster(String fromPath, String dstPath, String method, MbMessage msg)
			throws MessageBusException {
		
		RMB from = rootRMB.create(fromPath);
		try {
			msg.from(from.id());
			msg.to(dstPath);
			from.message().to(MessageBusServer.SERVER_ID + dstPath).data(JSON.toJSON(msg)).method(method).send();
		} catch (IOException e) {
			throw new MessageBusException("Failed to send data.", e);
		
		}
	}

	
	@Override
	public MbMessage request(String fromPath, String dstURI, String method,
			MbMessage msg) throws MessageBusException {
		
		Reply reply = null;
		
		try {
			reply = rootRMB.create(fromPath).request().to(dstURI).method(method).data(msg).execute().get();
		} catch (InterruptedException e) {
			new MessageBusException("The request thread is interrupted while waiting for response." ,e);
		} catch (IOException e) {
			new MessageBusException("The request thread is corrupted while waiting for response." ,e);
		}

		return reply == null ? null : JSON.fromJSON(reply.toString(), MbMessage.class);

	}
	
	@Override
	public synchronized String getURI() {
		
		while (!connected) {
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		
		return rootRMB.id();
		
	}
	
	@Override
	public void removeResource(String path) {
		RMB res = rootRMB.create(path);
		res.remove();
	}
	
	
	public synchronized boolean isConnected() {
		
		return connected;
		
	}

}
