package edu.cmu.mdnsim.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.spi.TrapHostingTransport.TrapHostable;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.util.StringUtil;

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
	private static String js;
	/**
	 * Used to handle platform specific file separators
	 */
	private static String _separator = File.separator;

	// Each of the following objects represent one hosted object. 
	// Add a new object for each new hosted file.  
	private static TrapHostable mainClientPage = new TrapHostable("text/html") {
		@Override
		public byte[] getBytes() {
			String src = StringUtil.toUtfString(getResourceBytes("client.html"));
			//The client.html file should have "warp.js" string in head.
			//That string will be replaced by the Warp.js code
			src = src.replace("warp.js", js);
			return StringUtil.toUtfBytes(src);
		}
	};
	private static TrapHostable displayGraphJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {

			return getResourceBytes("js"+ _separator + "displayGraph.js");
		}
	};
	private static TrapHostable sigmaJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.min.js");
		}
	};
	private static TrapHostable sigmaJsonParserJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.parsers.json.min.js");
		}
	};
	private static TrapHostable sigmaForceAtlas2Js = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "sigma.layout.forceAtlas2.min.js");
		}
	};
	private static TrapHostable bootstrapMinJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js" + _separator + "bootstrap.min.js");
		}
	};
	private static TrapHostable bootstrapMinCss = new TrapHostable("text/css") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("css" + _separator + "bootstrap.min.css");
		}
	};
	
	/**
	 * Reads the file from the folder and returns its bytes
	 * @param resourceName path relative to resources folder like "js\abc.js"
	 * @return byte[]
	 */
	private static byte[] getResourceBytes(String resourceName){
		InputStream is = WebClient.class.getClassLoader().getResourceAsStream(resourceName);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int read;				
		try {
			while ((read = is.read(buf)) > -1)
				bos.write(buf, 0, read);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bos.toByteArray();
	}

	/**
	 * Hosts the Resources (files) required for Web Client
	 * @param domain
	 * @throws WarpException
	 * @throws IOException
	 * @throws TrapException
	 */
	public static void load(WarpDomain domain) throws WarpException, IOException, TrapException{
		domain.getJSLibraryURI(true);
		js = domain.getEmbeddedJSWithAuthToken(true, true);
		
		domain.addHostedObject(mainClientPage, "index.html");
		domain.addHostedObject(displayGraphJs, "js/displayGraph.js");
		domain.addHostedObject(sigmaJs, "js/sigma.min.js");
		domain.addHostedObject(sigmaJsonParserJs, "js/sigma.parsers.json.min.js");
		domain.addHostedObject(sigmaForceAtlas2Js, "js/sigma.layout.forceAtlas2.min.js");
		domain.addHostedObject(bootstrapMinJs, "js/bootstrap.min.js");
		domain.addHostedObject(bootstrapMinCss, "css/bootstrap.min.css");
	}
}
