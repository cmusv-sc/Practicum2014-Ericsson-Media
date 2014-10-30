package edu.cmu.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utility {

	private static DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US);
	public static String currentTime(){
		Date date = new Date();
		return dateFormat.format(date);
	}


	public static String millisecondTimeToString(long endTime) {
		Date date = new Date(endTime);
		return dateFormat.format(date);
	}
	
}
