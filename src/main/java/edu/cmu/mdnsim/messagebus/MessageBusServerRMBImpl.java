package edu.cmu.mdnsim.messagebus;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import us.yamb.mb.util.JSON;
import us.yamb.rmb.Message;
import us.yamb.rmb.RMB;
import us.yamb.rmb.Response;
import us.yamb.rmb.Response.ResponseException;
import us.yamb.rmb.builders.RMBBuilder;
import us.yamb.rmb.callbacks.OnDelete;
import us.yamb.rmb.callbacks.OnGet;
import us.yamb.rmb.callbacks.OnPost;
import us.yamb.rmb.callbacks.OnPut;
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

	
	public void config() throws MessageBusException {
		
		JDKLoggerConfig.initForPrefixes(Level.INFO, "embedded");
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp", "com.ericsson");

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
		
		System.out.println("rmb uri: " + rmb.id());
		
//		try {
//			WebClient.load(broker.getServer().getHostingTransport("http"));
//		} catch (IOException e) {
//			throw new MessageBusException(e);
//		} catch (TrapException e) {
//			throw new MessageBusException(e);
//		}

		
	}

	public void send(String fromPath, String dstURI, String method,
			MbMessage msg) throws MessageBusException {
		
		
		try {
			rmb.message().to(dstURI).method(method).send();
		} catch (IOException e) {
			throw new MessageBusException(e);
		}
		
		
	}

	public void addMethodListener(String path, String method, final Object object,
			final String objectMethod) throws MessageBusException {
		
		RMB resource = rmb.create(path);
		
		if (method.trim().toUpperCase().equals("GET")) {
			
//			resource.onget(new OnGet() {
//				
//				private Method method = MessageBusServerRMBImpl.parseMethod(object, objectMethod);
//				
//				public void onget(Message message) throws ResponseException {
//					Class<?>[] params = this.method.getParameterTypes();
//					Object[]   objs   = new Object[params.length];
//					for (int i = 0; i < params.length; i++) {
//						Class<?> param = params[i];
//						objs[i] = JSON.fromJSON(message.toString(), param);
//					}
//					
//					try {
//						method.invoke(object, objs);
//					} catch (IllegalAccessException e) {
//						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
//					} catch (IllegalArgumentException e) {
//						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
//					} catch (InvocationTargetException e) {
//						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
//					}
//				}
//				
//			});
			
			resource.onget(new OnGet() {
				
				public void onget(Message message) throws ResponseException {
					System.out.println("hello");
				}
				
			});
			
			
		} else if (method.trim().toUpperCase().equals("PUT")) {
				
				resource.onput(new OnPut() {
				
				private Method method = MessageBusServerRMBImpl.parseMethod(object, objectMethod);
				
				public void onput(Message message) throws ResponseException {
					
					Class<?>[] params = this.method.getParameterTypes();
					Object[]   objs   = new Object[params.length];
					for (int i = 0; i < params.length; i++) {
						Class<?> param = params[i];
						objs[i] = JSON.fromJSON(message.toString(), param);
					}
					
					try {
						method.invoke(object, objs);
					} catch (IllegalAccessException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (IllegalArgumentException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (InvocationTargetException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					}
				}
				
			});
			
			
		} else if (method.trim().toUpperCase().equals("DELETE")) {
			
			resource.ondelete(new OnDelete() {
				
				private Method method = MessageBusServerRMBImpl.parseMethod(object, objectMethod);
				
				public void ondelete(Message message) throws ResponseException {
					
					Class<?>[] params = this.method.getParameterTypes();
					Object[]   objs   = new Object[params.length];
					for (int i = 0; i < params.length; i++) {
						Class<?> param = params[i];
						objs[i] = JSON.fromJSON(message.toString(), param);
					}
					
					try {
						method.invoke(object, objs);
					} catch (IllegalAccessException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (IllegalArgumentException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (InvocationTargetException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					}
				}
				
			});
		} else if (method.trim().toUpperCase().equals("POST")) {
			
			resource.onpost(new OnPost() {
				
				private Method method = MessageBusServerRMBImpl.parseMethod(object, objectMethod);
				
				public void onpost(Message message) throws ResponseException {
					
					Class<?>[] params = this.method.getParameterTypes();
					Object[]   objs   = new Object[params.length];
					for (int i = 0; i < params.length; i++) {
						Class<?> param = params[i];
						objs[i] = JSON.fromJSON(message.toString(), param);
					}
					
					try {
						method.invoke(object, objs);
					} catch (IllegalAccessException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (IllegalArgumentException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					} catch (InvocationTargetException e) {
						Response.create().status(Response.Status.CLIENT_ERROR).throwException();
					}
				}
				
			});
		}
		
	}

//	private void configService() throws MessageBusException {
//
//		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp", "com.ericsson");
//
//		try {
//			svc=warpDomain.createService("mdn-manager");
//			//_svc = Warp.init().service(Master.class.getName(), "embedded", "mdn-manager").create();
//			//.setDescriptorProperty(ServicePropertyName.LOOKUP_SERVICE_ENDPOINT,"ws://localhost:9999").create();
//			svc.notifications().registerForNotification(Notifications.Registered, new Listener() {
//
//				@Override
//				public void receiveNotification(String name, Object sender, Object attachment) {
//					WarpLogger.info("Now registered...");
//				}
//			}, true);
//		} catch (WarpException e) {
//			throw new MessageBusException(e);
//		}
//		
//		warpAppl = WarpContext.getApplication();
//	}
	
	
	public static Method parseMethod(Object object, String name) {
		Method[] methods = object.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new RuntimeException("Cannot found method with name: " + name);
	}

}
