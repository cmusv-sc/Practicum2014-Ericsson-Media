package edu.cmu.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Analysis {
	
	public static void analysis() {
		try {
			
			BufferedReader srcBr = new BufferedReader(new FileReader("./analysis/test4/src -1426980701520.log"));
			BufferedReader sinkBr = new BufferedReader(new FileReader("./analysis/test4/sink -1426980701564.log"));
			
			BufferedWriter bw = new BufferedWriter(new FileWriter("./analysis.log"));
			BufferedWriter lostListBr = new BufferedWriter(new FileWriter("./lostList.log"));
			
			String srcRec = null;
			String sinkRec = null;
			
			List<Long> droppedList = new ArrayList<Long>();
			List<Long> lostList = new ArrayList<Long>();
			
			while((srcRec = srcBr.readLine()) != null) {
				droppedList.add(parseLostPacketId(srcRec));
			}
			
			while((sinkRec = sinkBr.readLine()) != null) {
				for (String str : sinkRec.split("\t")[1].split("\\s")) {
					if (str.charAt(0) == '!') {
						lostList.add(parseLostPacketId(str));
					} else if (str.charAt(0) == '@') {
						System.err.println(str);
					}
				}
			}
			
			for (int i = 0; i < lostList.size(); i++) {
				lostListBr.write("![" + lostList.get(i) + "]\n");
			}
			
			int i = 0, j = 0;
			
			
			while (i < droppedList.size() && j < lostList.size()) {
				bw.write("[" + i + " , " + j + "]\t");
				if (droppedList.get(i) == lostList.get(j)) {
					i++;
					j++;
					bw.write("E\t![" + droppedList.get(i) + "]\t#[" + lostList.get(j) + "]");
				} else if (droppedList.get(i) > lostList.get(j)){
					bw.write("S\t![" + droppedList.get(i) + "]\t#[" + lostList.get(j) + "]");
					j++;
				} else {
					bw.write("L\t![" + droppedList.get(i) + "]\t#[" + lostList.get(j) + "]");
					i++;
				}
				bw.write("\n");
			}
			
			bw.flush();
			bw.close();
			
			
			
//			for (int i = 0; i < droppedList.size(); i++) {
//				System.out.print(droppedList.get(i) + " ");
//			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		Analysis.analysis();
	}

	private static long parseLostPacketId(String str) {
		return Long.parseLong(str.substring(2, str.length()-1));
	}
}
