package edu.cmu.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
}
