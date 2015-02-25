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

	// Each of the following objects represent one hosted object. 
	// Add a new object for each new hosted file.  
	public static TrapHostable mainClientPage = new TrapHostable("text/html") {
		@Override
		public byte[] getBytes() {
			String src = StringUtil.toUtfString(getResourceBytes("client.html"));
			//The client.html file should have "warp.js" string in head.
			//That string will be replaced by the Warp.js code
			return StringUtil.toUtfBytes(src);
		}
	};
	public static TrapHostable displayGraphJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {

			return getResourceBytes("js"+ _separator + "displayGraph.js");
		}
	};
	public static TrapHostable sigmaJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.min.js");
		}
	};
	public static TrapHostable sigmaJsonParserJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.parsers.json.min.js");
		}
	};
	public static TrapHostable sigmaForceAtlas2Js = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.layout.forceAtlas2.min.js");
		}
	};
	public static TrapHostable bootstrapMinJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "bootstrap.min.js");
		}
	};
	public static TrapHostable bootstrapMinCss = new TrapHostable("text/css") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("css" + _separator + "bootstrap.min.css");
		}
	};
	
	
	public static TrapHostable rmb_ambJs = new TrapHostable("text/javascript") {
		
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "rmb-amb.js");
		}
		
		
	};
	
	public static TrapHostable amb_tmbJs = new TrapHostable("text/javascript") {
		
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "amb-tmb.js");
		}
		
		
	};
	
	public static TrapHostable tmbJs = new TrapHostable("text/javascript") {
		
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "tmb.js");
		}
		
		
	};
	
	public static TrapHostable trap_fullJs = new TrapHostable("text/javascript") {
		
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "trap-full.js");
		}
		
		
	};
	
	/**
	 * Reads the file from the folder and returns its bytes
	 * @param resourceName path relative to resources folder like "js\abc.js"
	 * @return byte[]
	 */
	public static byte[] getResourceBytes(String resourceName){
		
		InputStream is = WebClient.class.getClassLoader().getResourceAsStream(resourceName);
		if(is == null){
			resourceName = "resources/" + resourceName.replace('\\','/');	
			is = WebClient.class.getClassLoader().getResourceAsStream(resourceName);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int read;				
		try {
			while ((read = is.read(buf)) > -1) {
				bos.write(buf, 0, read);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bos.toByteArray();
	}
	
	static class MyRequestHandler extends TrapHostable implements RequestHandler {
 
			public void handleRequest(Request request, Response response) {
				
				String resourceName = request.getUri().substring(7);
				System.out.println("Got request for: " + resourceName);
				
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
	
	public static MyRequestHandler handler = new MyRequestHandler();

	/**
	 * Hosts the Resources (files) required for Web Client
	 * @param domain
	 * @throws WarpException
	 * @throws IOException
	 * @throws TrapException
	 */
	public static void load(TrapHostingTransport domain) throws IOException, TrapException{
		
		
		String indexUri;// = domain.addHostedObject(mainClientPage, "index.html").toString();
		
		/*domain.addHostedObject(rmb_ambJs, "js/rmb-amb.js");
		domain.addHostedObject(amb_tmbJs, "js/amb-tmb.js");
		domain.addHostedObject(tmbJs, "js/tmb.js");
		domain.addHostedObject(trap_fullJs, "js/trap-full.js");
		
		
		
		domain.addHostedObject(displayGraphJs, "js/displayGraph.js");
		domain.addHostedObject(sigmaJs, "js/sigma.min.js");
		domain.addHostedObject(sigmaJsonParserJs, "js/sigma.parsers.json.min.js");
		domain.addHostedObject(sigmaForceAtlas2Js, "js/sigma.layout.forceAtlas2.min.js");
		domain.addHostedObject(bootstrapMinJs, "js/bootstrap.min.js");
		domain.addHostedObject(bootstrapMinCss, "css/bootstrap.min.css");*/
		indexUri = domain.addHostedObject(handler, "files").toString();
		
		System.out.println("client: " + indexUri);
	}
}
