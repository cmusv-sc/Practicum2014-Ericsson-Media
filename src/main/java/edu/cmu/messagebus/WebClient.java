package edu.cmu.messagebus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.spi.TrapHostingTransport.TrapHostable;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.util.StringUtil;

public class WebClient {
	private static String js;  
	/**
	 * 
	 * @param resourceName path relative to resources folder like "js\abc.js"
	 * @return byte[]
	 */
	public static byte[] getResourceBytes(String resourceName){
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
	private static TrapHostable mainClientPage = new TrapHostable("text/html") {
		@Override
		public byte[] getBytes() {
			String src = StringUtil.toUtfString(getResourceBytes("client.html"));
			src = src.replace("warp.js", js);
			return StringUtil.toUtfBytes(src);
		}
	};
	private static TrapHostable displayGraphJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js\\displayGraph.js");
		}
	};
	private static TrapHostable sigmaJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js\\sigma.min.js");
		}
	};
	private static TrapHostable sigmaJsonParserJs = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js\\sigma.parsers.json.min.js");
		}
	};
	private static TrapHostable sigmaForceAtlas2Js = new TrapHostable("text/javascript") {
		@Override
		public byte[] getBytes() {
			return getResourceBytes("js\\sigma.layout.forceAtlas2.min.js");
		}
	};

	public void load(WarpDomain domain) throws WarpException, IOException, TrapException{
		domain.getJSLibraryURI(true);
		js = domain.getEmbeddedJSWithAuthToken(true, true);
		System.out.println(domain.addHostedObject(mainClientPage, "index.html"));
		System.out.println(domain.addHostedObject(displayGraphJs, "js/displayGraph.js"));
		System.out.println(domain.addHostedObject(sigmaJs, "js/sigma.min.js"));
		System.out.println(domain.addHostedObject(sigmaJsonParserJs, "js/sigma.parsers.json.min.js"));
		System.out.println(domain.addHostedObject(sigmaForceAtlas2Js, "js/sigma.layout.forceAtlas2.min.js"));
	}

}
