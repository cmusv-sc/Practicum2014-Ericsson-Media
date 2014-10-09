package edu.cmu.messagebus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ericsson.research.trap.TrapException;
import com.ericsson.research.trap.spi.TrapHostingTransport.TrapHostable;
import com.ericsson.research.warp.api.WarpDomain;
import com.ericsson.research.warp.api.WarpException;
import com.ericsson.research.warp.util.StringUtil;

import edu.cmu.messagebus.message.WebClientUpdateMessage;
import edu.cmu.messagebus.message.WebClientUpdateMessage.Node;

public class WebClient {
	private static String js;  
	private static String _separator = File.separator;
	
	private Map<String, WebClientUpdateMessage.Node> nodes = new HashMap<String, WebClientUpdateMessage.Node>();
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
			System.err.println("JEREMY DEBUG:" + "js" + _separator + "sigma.layout.forceAtlas2.min.js");
			return getResourceBytes("js" + _separator + "sigma.layout.forceAtlas2.min.js");
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

	public void addNode(WebClientUpdateMessage.Node newNode){
		this.nodes.put(newNode.id, newNode);
	}
	public void removeNode(WebClientUpdateMessage.Node node){
		this.nodes.remove(node.id);
	}
	public List<WebClientUpdateMessage.Node> getNodes(){
		return (List<Node>) this.nodes.values();
	}
	
	
}
