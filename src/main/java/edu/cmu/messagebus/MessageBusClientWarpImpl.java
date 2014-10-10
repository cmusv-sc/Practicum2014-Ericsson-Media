package edu.cmu.messagebus;

import java.util.logging.Level;

import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.client.AnonymousClient;
import com.ericsson.research.warp.api.client.Client;
import com.ericsson.research.warp.api.client.Client.ConnectionPolicy;
import com.ericsson.research.warp.api.client.PlaintextAuthenticator;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.messagebus.exception.MessageBusException;
import edu.cmu.messagebus.message.Message;
import edu.cmu.messagebus.message.NodeRegistrationRequest;

public class MessageBusClientWarpImpl implements MessageBusClient {

	private AnonymousClient _client;

	@Override
	public void config() throws MessageBusException {
		/* Initialize the message bus */
		JDKLoggerConfig.initForPrefixes(Level.INFO, "warp");

		String trapCfg = "trap.transport.websocket.wsuri=ws://127.0.0.1:8889\n"
				+ "trap.transport.http.enabled=false\n"
				+ "trap.transport.socket.enabled=false\n"
				+ "trap.transport.loopback.enabled=false";

		try {
			_client = Warp
					.init()
					.client()
					.setRemoteConfig(trapCfg)
					.setAuth(
							new PlaintextAuthenticator(WarpURI
									.create("warp:anon/foo"), "secret"))
					.setPolicy(ConnectionPolicy.CLOSE_ON_DISCONNECT)
					.createAnonymous();
		} catch (WarpException e1) {
			throw new MessageBusException(e1);
		}

		_client.notifications().registerForNotification(
				Client.ConnectedNotification, new Listener() {
					
					@Override
					public void receiveNotification(String name, Object sender,
							Object attachment) {
						WarpLogger
								.info("Connection successful. Time to do stuff!");
						WarpURI nodeURI = Warp.uri();
						NodeRegistrationRequest registMsg = 
								new NodeRegistrationRequest();
						registMsg.setWarpURI(nodeURI.toString());
						
						try {
							Warp.send("/", WarpURI.create("warp://cmu-sv:mdn-manager/discover"),
									"POST", JSON.toJSON(registMsg).getBytes());
						} catch (WarpException e) {
							e.printStackTrace();
						}
					}
				}, true);

		_client.notifications().registerForNotification(
				Client.DisconnectedNotification, new Listener() {

					@Override
					public void receiveNotification(String name, Object sender,
							Object attachment) {
						WarpLogger
								.info("We have been disconnected from the server");
					}
				}, true);

	}
	
	@Override
	public void connect() throws MessageBusException {
		
		try {
			_client.connect();
		} catch (WarpException e) {
			throw new MessageBusException(e);
		}
		
	}



	@Override
	public void addMethodListener(String path, String method,
			Object object, String objectMethod) throws MessageBusException {
		
		try {
			Warp.addMethodListener(path, method, object, objectMethod);
		} catch (WarpException e) {
			e.printStackTrace();
			throw new MessageBusException("Failed to add method listener by Warp.", e);
		}
		
	}

	@Override
	public void send(String fromPath, String dstURI, String method, Message msg)
			throws MessageBusException {
		
		try {
			
			Warp.send(fromPath, 
					WarpURI.create(dstURI), method, JSON.toJSON(msg).getBytes());
		
		} catch (WarpException e) {
			throw new MessageBusException("Failed to send data.", e);
		}
		
	}



	@Override
	public void sendToMaster(String fromPath, String method, Message msg)
			throws MessageBusException {
		
		try {
			Warp.send(fromPath, WarpURI.create("warp://cmu-sv:mdn-manager/report"), method, JSON.toJSON(msg).getBytes());
		} catch (WarpException e) {
			throw new MessageBusException("Failed to send data.", e);
		
		}
	}
}
