package edu.cmu.config;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import com.ericsson.research.warp.util.JSON;

public class test {

	public static void main(String[] args){
		String script = args[0];
		readScript(script);
	}
	
	
	public static void readScript(String fileName){
		if(fileName == null){
			return;
		}
		
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(new File(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
/*		List<NodeConfig> list = new LinkedList<NodeConfig>();
		NodeConfig.Builder builder1 = new NodeConfig.Builder("Source");
		NodeConfig nodeConfig1 = builder1.bitrate(500).build();
		
		NodeConfig.Builder builder2 = new NodeConfig.Builder("Sink");
		NodeConfig nodeConfig2 = builder2.bitrate(500).build();
		list.add(nodeConfig1);
		list.add(nodeConfig2);
		
		StreamConfig stream = new StreamConfig();
		stream.setFlow(list);
		
		
		String str = JSON.toJSON(stream);
		System.out.println(str);*/
		
		StreamConfig stream2 = JSON.fromJSON(inputStream, StreamConfig.class);
		System.out.println(stream2.getFlow().get(0).getBitrate());
	}
}
