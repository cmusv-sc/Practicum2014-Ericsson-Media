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
	 static TrapHostable hosted = new TrapHostable("text/html") {
			
			@Override
			public byte[] getBytes() {
				
				InputStream is = WebClient.class.getClassLoader().getResourceAsStream("client.html");
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				
				int read;
				
				try {
					while ((read = is.read(buf)) > -1)
						bos.write(buf, 0, read);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				String src = StringUtil.toUtfString(bos.toByteArray());
				src = src.replace("warp.js", js);
				
				return StringUtil.toUtfBytes(src);
			}
		};
		
		public void load(WarpDomain domain) throws WarpException, IOException, TrapException{
			
	        domain.getJSLibraryURI(true);
	        
	        js = domain.getEmbeddedJSWithAuthToken(true, true);
	        
	        System.out.println(domain.addHostedObject(hosted, "index.html"));

		}
		
}
