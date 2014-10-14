package edu.cmu.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import com.ericsson.research.warp.util.JSON;

public class ScriptReader {

	public WorkConfig getWorkConfigFromScript(String fileName){
		if(fileName == null){
			return null;
		}
		
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(new File(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	
		return  JSON.fromJSON(inputStream, WorkConfig.class);
		//JSONDeserializer jsonDeserializer = new JSONDeserializer();
		//return (StreamConfig) jsonDeserializer.deserialize(new InputStreamReader(inputStream), StreamConfig.class);
	}
	
	public StreamConfig getStreamConfigFromScript(String fileName){
		if(fileName == null){
			return null;
		}
		
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(new File(fileName));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	
		return  JSON.fromJSON(inputStream, StreamConfig.class);
		//JSONDeserializer jsonDeserializer = new JSONDeserializer();
		//return (StreamConfig) jsonDeserializer.deserialize(new InputStreamReader(inputStream), StreamConfig.class);
	}
}
