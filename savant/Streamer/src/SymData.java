import java.util.Arrays;

//data structure for each subscribed symbol
//it maintains a window of volume and Price*Vol to provide moving average
//of different intervals. Based on need, we update the moving average for
//certain intervals anytime we receive new data or update request from client
//
//Open, Close, high, low data have be calculated based on need. And it is not usual for a client
//to ask for moving open/close/high/low data. 
//

//pos always points to the last updated data
//The data returned in get function is 1 second in delay to allow delayed AT data
//Eg:
//t= 1   2   3   4   5   6   7   8 
//                     pos
//when pos is 6, we assume data at t=5 is not complete, so the latest data we return is t=4 Therefore, to provide 3600s moving average, the window size  needs to be 3602  


public class SymData {
	public static String[] allowedIntervalText = {"1s", "5s", "10s", "30s", "1m", "5m", "10m", "30m", "1h"};
	public static int[] allowedInterval = {1, 5, 10, 30, 60, 300, 600, 1800, 3600};
	
	private long[] wv;
	private long[] wp;
	private int pos;
	private long lastSecond;
	private long[] tv;
	private long[] tp;

	private boolean[] mvSwitch;
	private long totalSeconds;
	
	private int intnum;
	private int winsize;

			
	public SymData(){
		intnum = allowedInterval.length;
		//to calculate the moving average of 3600, we need array of 3602 
		winsize = allowedInterval[intnum-1]+2;
		
		wv = new long[winsize];
		wp = new long[winsize];
		pos = 0;
		lastSecond = 0;
		totalSeconds = 0;
		tv = new long[intnum];
		tp = new long[intnum];
		Arrays.fill(tv, -1);
		Arrays.fill(tp, -1);
		
	}

	// called everytime a tick data is received or a client request is received
	//type: 0 tick data 1: client request
	//second has to be an interger reflecting current time provided  by the caller

	public void update(long second, long vol, double price, int type){
		synchronized(this){ 
			System.out.print("SymDataUpdating");
			
			if (second < lastSecond -1){
				//this not likely happen, as second is a value generated by Streamer based on event time (not timestamp)
				System.out.println("Data with delay more than 1 second is not accepted:"+ String.valueOf(second)+", last second is "+String.valueOf(lastSecond));
				return;
			}
			
			long pv = 0;  
			
			// if still in the same second, then just update the current wv and wp value and done
			if (lastSecond == second){
				System.out.println("================================ POS IS " + pos);
				if (type == 0){
					pv = (long) (price * vol);
					wv[pos]+=vol;
					wp[pos]+=pv;
					
				}
				return;
			}
			else if(lastSecond == second+1){
				pos = pos + 1;
				System.out.println("New Data, POS IS " + pos);
				if(type == 0){
					pv = (long) (price * vol);
					//wv[pos-1]+=vol; //array out of bunder
					wv[pos]=vol;
					wp[pos]=pv;
				}
				System.out.println("!= 0");
			//This branch should be impossible, if second smaller than lastSecond, it must be because of an tick data. 
				return;
			//}else{
				//System.out.println(">1");
			}
	
			//update the moving average values

			//skip is number of the seconds between last update and this one.
			System.out.println("skip");
			int skip = 0;
			if(lastSecond != 0)
				skip = (int) (second - lastSecond -1);

			totalSeconds = totalSeconds + skip + 1;
			lastSecond = second;

			//skip cannot be longer than 3601. 
			if(skip > winsize-1)
				skip = winsize-1;
			
			//update the moving averages first
			for (int i=0; i<intnum; i++){
				//if skip is too big, then existing moving average is not relevant
				if (skip>=allowedInterval[i]+1)
					tv[i]=tp[i]=0;
				else if(skip == allowedInterval[i]){
					tv[i]=wv[pos];
					tp[i]=wp[pos];
				}
				//Deduct the values leaving the window. Since values at pos and pos-1 were not counted in the previous moving average, we need to add them first.
				else{ 
					if(skip>0){
						tv[i]+=wv[pos];
						tp[i]+=wp[pos];
					}
					tv[i]+=wv[pos-1];
					tp[i]+=wp[pos-1];

					for(int j=0; j<skip; j++){
						tv[i] -= wv[(pos-1-allowedInterval[i]+j)%winsize];
						tp[i] -= wp[(pos-1-allowedInterval[i]+j)%winsize];

					}
				}
			}
			//fill 0 to the skipped spots
			for (int i=0; i<skip; i++){ 
				wv[(pos+i+1)%winsize]=0;
				wp[(pos+i+1)%winsize]=0;
			}
			//update pos and change the value at new pos
			pos= (pos+skip+1)%winsize;
			wv[pos]=vol;
			wp[pos]=pv;
		}
	}
	
	public String getBar(long second, int interval, int bar_mask){
		
		update(second, 0, 0, 1);
		String retval = "";
		//price average
		long pave;
		
		if (interval >= intnum)
			return "";
		int ival = allowedInterval[interval];
	
		if((bar_mask & 0x20) !=0){
		//average of specified interval
			retval += String.valueOf(tp[interval]/tv[interval]);
		}
		retval += ",";
		if((bar_mask & 0x10) != 0){
		//open of specified interval
			retval += String.valueOf(wp[(pos-1-ival)%winsize]/tv[(pos-1-ival)%winsize]);
		}
		retval += ",";
		if((bar_mask & 0x08) != 0){
		//close of specified interval
			retval += String.valueOf(wp[(pos-2)%winsize]/wv[(pos-2)%winsize]);
		}
		retval += ",";
		if(((bar_mask & 0x04) != 0) || ((bar_mask & 0x02) != 0)){
		//high or close, scan the period anyway
			long h = -1;
			long l = 1000000;
			for(int i=0; i<ival; i++){
				pave = wp[(pos-2-i)%winsize]/wv[(pos-2-i)%winsize];
				if ( pave> h)
					h=pave;
				if (pave < l)
					l=pave;
			}
			if((bar_mask & 0x04) != 0)
				retval += String.valueOf(h);
			retval += ",";
			if((bar_mask & 0x02) != 0)
				retval += String.valueOf(l);
			retval += ",";
		}
		if((bar_mask & 0x1) != 0)
			retval += String.valueOf(tv[interval]);
		
		return retval; 
	}

	public String getMA(int second, int ma_mask){
		
		update(second, 0, 0, 1);
		String retval = "";

		for(int i=0;i<intnum; i++){
			if((ma_mask & (0x1 << i)) != 0){
				retval += String.valueOf(tp[i]/tv[i]);
				retval += ":";
				retval += String.valueOf(tv[i]);
			}
			if (i< intnum-1)
				retval += ",";
		}
		return retval;
	}
}
