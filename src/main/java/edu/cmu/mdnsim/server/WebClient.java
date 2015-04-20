package edu.cmu.mdnsim.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.nhttpd.Request;
import com.ericsson.research.trap.nhttpd.RequestHandler;
import com.ericsson.research.trap.nhttpd.Response;
import com.ericsson.research.trap.spi.TrapHostingTransport;
import com.ericsson.research.trap.spi.TrapHostingTransport.TrapHostable;
import com.ericsson.research.trap.utils.StringUtil;

/**
 * Used to host the different files (html,js) required by the WebClient
 *  
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class WebClient {
	/**
	 * used to include Warp.js code in the html file.
	 */
	public static String js;
	/**
	 * Used to handle platform specific file separators
	 */
	public static String _separator = File.separator;

	
	static class WebRequestHandler extends TrapHostable implements RequestHandler {
 
			public void handleRequest(Request request, Response response) {
				
				String resourceName = request.getUri().substring(7);
				
				InputStream is = WebClient.class.getClassLoader().getResourceAsStream(resourceName);
				if(is == null){
					resourceName = "resources/" + resourceName.replace('\\','/');	
					is = WebClient.class.getClassLoader().getResourceAsStream(resourceName);
				}
				
				if (is == null)
				{
					response.setStatus(404);
					response.setData("Not Found");
				}
				else
				{
					String content = "text/html";
			        
			        if (resourceName.endsWith(".js"))
			            content = "application/javascript";
			        
			        if (resourceName.endsWith(".css"))
			            content = "text/css";
			        
			        if (resourceName.endsWith(".svg"))
			            content = "image/svg+xml";
			        
			        if (resourceName.endsWith(".png"))
			            content = "image/png";
			        
			        response.addHeader("Content-Type", content);
					response.setStatus(200);
					response.setData(is);
				}
				
			}

			@Override
			public byte[] getBytes() {
				// TODO Auto-generated method stub
				return null;
			}
			
	}
	
	public static WebRequestHandler handler = new WebRequestHandler();

	/**
	 * Hosts the Resources (files) required for Web Client
	 * @param domain
	 * @throws WarpException
	 * @throws IOException
	 * @throws TrapException
	 */
	public static void load(TrapHostingTransport domain) throws IOException, TrapException{
		
		String indexUri = domain.addHostedObject(handler, "files").toString();
		
		System.out.println("client: " + indexUri);
	}
}
