package edu.cmu.mdnsim.messagebus;

import java.io.IOException;
import java.util.logging.Level;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.utils.JDKLoggerConfig;
import com.ericsson.research.warp.api.Notifications;
import com.ericsson.research.warp.api.Notifications.Listener;
import com.ericsson.research.warp.api.Warp;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.api.WarpInit.DomainInit;
import com.ericsson.research.warp.api.WarpInit.DomainInit.BuiltinService;
import com.ericsson.research.warp.api.WarpService;
import com.ericsson.research.warp.api.WarpURI;
import com.ericsson.research.warp.api.logging.WarpLogger;
import com.ericsson.research.warp.util.JSON;

import edu.cmu.mdnsim.messagebus.exception.MessageBusException;
import edu.cmu.mdnsim.messagebus.message.MbMessage;
import edu.cmu.mdnsim.server.WebClient;

public class MessageBusServerWarpImpl implements MessageBusServer {

	private static WarpDomain _warpDomain;



	private static WarpService _svc;

	@Override
	public void config() throws MessageBusException {

		JDKLoggerConfig.initForPrefixes(Level.ALL, "embedded");
		JDKLoggerConfig.initForPrefixes(Level.WARNING, "warp", "com.ericsson");
		configDomain();

		try {
			//Load the web client 
			//A TrapHostable that hosts the Web Interface for the Mdn Simulator
			WebClient.load(_warpDomain);
		} catch (WarpException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TrapException e) {
			e.printStackTrace();
		}

		configService();
	}

	@Override
	public void send(String fromPath, String dstURI, String method, MbMessage msg)
			throws MessageBusException {

		try {
			Warp.send(fromPath, WarpURI.create(dstURI), method, JSON.toJSON(msg).getBytes());
		} catch (WarpException e) {
			throw new MessageBusException(e);
		}

	}

	@Override
	public void addMethodListener(String path, String method, Object object,
			String objectMethod) throws MessageBusException {
		try {
			Warp.addMethodListener(path, method, object, objectMethod);
		} catch (WarpException e) {
			throw new MessageBusException(e);
		}

	}


	private void configDomain() throws MessageBusException {

		try {
			DomainInit domainInit = Warp.init().domain();

			// Configure the gateway (client connections) to go to http://127.0.0.1:8888 as initial connection
			domainInit.getClientNetworkCfg().setBindHost("127.0.0.1").setBindPort("http", 8888).setBindPort("websocket", 8889).finish();

			// Configure the lookup service (service registry) to bind to http://127.0.0.1:9999 as initial connection
			domainInit.getServiceNetworkCfg(BuiltinService.LOOKUP_SERVICE).setBindHost("127.0.0.1").setBindPort("websocket", 9999).finish();

			// Add any additional (built-in servers) in the com.ericsson.research.warp.spi.enabled package and start
			_warpDomain = domainInit.loadWarpEnabled(true).create();

			System.out.println(_warpDomain.getTestClientURI());
		} catch (WarpException e) {
			throw new MessageBusException(e);
		}
	}

	private void configService() throws MessageBusException {

		JDKLoggerConfig.initForPrefixes(Level.WARNING, "warp", "com.ericsson");

		try {
			_svc=_warpDomain.createService("mdn-manager");
			//_svc = Warp.init().service(Master.class.getName(), "embedded", "mdn-manager").create();
			//.setDescriptorProperty(ServicePropertyName.LOOKUP_SERVICE_ENDPOINT,"ws://localhost:9999").create();
			_svc.notifications().registerForNotification(Notifications.Registered, new Listener() {

				@Override
				public void receiveNotification(String name, Object sender, Object attachment) {
					WarpLogger.info("Now registered...");
				}
			}, true);
		} catch (WarpException e) {
			throw new MessageBusException(e);
		}
	}

}
