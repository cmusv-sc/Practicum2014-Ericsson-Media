package edu.cmu.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 
 * @author Geng Fu
 * @author Jigar Patel
 * @author Vinay Kumar Vavili
 * @author Hao Wang
 *
 */
public class Utility {
	
	public static String currentTime(){
		Date date = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		return dateFormat.format(date);
	}

	public static String millisecondTimeToString(long endTime) {
		Date date = new Date(endTime);
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		return dateFormat.format(date);
	}
	
	public static long stringToMillisecondTime(String time) throws ParseException {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		return dateFormat.parse(time).getTime();
	}
	/**
	 * Adds the node Id and time in front of the log message
	 * [Time][NodeId][Message]
	 * @param message
	 * @param nodeId
	 * @return
	 */
	public static String getFormattedLogMessage(String message, String nodeId){
		Date date = new Date();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
		StringBuilder sb = new StringBuilder();
		sb.append("["); sb.append(dateFormat.format(date));
		sb.append("]["); sb.append(nodeId); 
		sb.append("]["); sb.append(message);
		sb.append("]");
		return sb.toString();
	}
}
