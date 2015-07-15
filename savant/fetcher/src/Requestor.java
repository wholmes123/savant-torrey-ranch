import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import at.feedapi.ActiveTickStreamListener;
import at.utils.jlib.PrintfFormat;
import at.shared.ATServerAPIDefines;
import at.shared.ATServerAPIDefines.ATBarHistoryResponseType;
import at.shared.ATServerAPIDefines.ATMarketMoversDbResponseType;
import at.shared.ATServerAPIDefines.ATTICKHISTORY_QUOTE_RECORD;
import at.shared.ATServerAPIDefines.ATTICKHISTORY_TRADE_RECORD;
import at.shared.ATServerAPIDefines.ATTickHistoryRecordType;
import at.shared.ATServerAPIDefines.ATTickHistoryResponseType;

public class Requestor extends at.feedapi.ActiveTickServerRequester
{
	ATTickDataFetcher m_fetcher;
	String fetchingSym;
	String premarketFilePath;
	String marketFilePath;
	String aftermarketFilePath;


/*
	Algorithm to find market open/close signal:
	1) bookkeep records from 9:30:00 until 9:30:05 or the first 100 records: 
	2)if there is one record with cond = 9 (NASDAQ symbosl), assert the following record have same price and size. Remove this record, all records before it belong to pre-market
	3)else(NYSE or AMEX symbols), found the record with maximum size, all records before it belong to pre-market, assert the exch be 78(NYSE) or 65(AMEX)
	Same for after-market(change from 16:00:00-16:05:00 or the first 100 records), a closing 

	state machine: 
	state 0: init: premarket
	state 1: time>9:30:00: to find market open signal, save data to temp_list
	state 2: rec with cond=9 found, or time>=9:30:05 or len(temp_list)=100: markethour
	state 2.1: if cond=9 was found, the next record should have same price and val, remove this record
	state 3: time>16:0:0: to find market close signal, save data to temp_list
	state 4: rec with cond=9 found, or time>16:05:00 or len(temp_list)==100: aftermarket
	state 4.1 if cond =9 was found, the next record should have same price and val, remove this record
*/				 
	int state; // can be 0 to 4, see comments in Requestor.java
	String date;
	int numRec;
	long[] vol = new long[100];
	double[] price= new double[100];
	String[] rec= new String[100];
	boolean crossRecVerified; 


	Logger logger = Logger.getLogger(ATTickDataFetcher.class.getName());
	//SavantLogger logger;

	public Requestor(APISession apiSession, ActiveTickStreamListener streamer, ATTickDataFetcher fetcher)
	{
		super(apiSession.GetServerAPI(), apiSession.GetSession(), streamer);
		m_fetcher = fetcher;
	}
	
	public void OnRequestTimeoutCallback(long origRequest)
	{
		System.out.println("(" + origRequest + "): Request timed-out");
	}

	public void OnTickHistoryDbResponse(long origRequest, ATServerAPIDefines.ATTickHistoryResponseType responseType, Vector<ATServerAPIDefines.ATTICKHISTORY_RECORD> vecData)
	{
		String strResponseType = "";
		switch(responseType.m_responseType)
		{
			case ATTickHistoryResponseType.TickHistoryResponseSuccess: strResponseType = "TickHistoryResponseSuccess"; break;
			case ATTickHistoryResponseType.TickHistoryResponseInvalidRequest: strResponseType = "TickHistoryResponseInvalidRequest"; break;
			case ATTickHistoryResponseType.TickHistoryResponseMaxLimitReached: strResponseType = "TickHistoryResponseMaxLimitReached"; break;
			case ATTickHistoryResponseType.TickHistoryResponseDenied: strResponseType = "TickHistoryResponseDenied"; break;
			default: break;
		}
		
		System.out.println("RECV (" + origRequest +"): Tick history response [" + strResponseType + "]\n--------------------------------------------------------------");

		Iterator<ATServerAPIDefines.ATTICKHISTORY_RECORD> itrDataItems = vecData.iterator();
		int index = 0;
		int recCount = vecData.size();

		if (recCount >= 100000) {
			System.out.println("Overloaded: " + recCount);
			m_fetcher.onTickHistoryOverload();
			return;
		}

		String strFormat = "%0.2f";
		ArrayList<String> premarketTickRecords = new ArrayList<String>();
		ArrayList<String> marketTickRecords = new ArrayList<String>();
		ArrayList<String> aftermarketTickRecords = new ArrayList<String>();
		while(itrDataItems.hasNext())
		{	
			ATServerAPIDefines.ATTICKHISTORY_RECORD record = (ATServerAPIDefines.ATTICKHISTORY_RECORD)itrDataItems.next();
			switch(record.recordType.m_historyRecordType) {
				case ATTickHistoryRecordType.TickHistoryRecordTrade:
				{
					ATTICKHISTORY_TRADE_RECORD atTradeRecord = (ATTICKHISTORY_TRADE_RECORD)record; 
					StringBuilder sb = new StringBuilder();
					//date
					sb.append(atTradeRecord.lastDateTime.month+ "/" + atTradeRecord.lastDateTime.day + "/" + atTradeRecord.lastDateTime.year);   
					sb.append(" ");
					//time
					sb.append(atTradeRecord.lastDateTime.hour + ":" + atTradeRecord.lastDateTime.minute + ":" + atTradeRecord.lastDateTime.second);
					sb.append(",");
					//type
					sb.append("TRADE");
					sb.append(",");
					//price
					strFormat = "%0." + atTradeRecord.lastPrice.precision + "f";
					sb.append( new PrintfFormat(strFormat).sprintf(atTradeRecord.lastPrice.price));
					sb.append(",");
					//vol
					sb.append(atTradeRecord.lastSize);
					sb.append(",");
					//exch
					sb.append(atTradeRecord.lastExchange.m_atExchangeType);
					sb.append(",");
					//condition
					sb.append(atTradeRecord.lastCondition[0].m_atTradeConditionType);

//					sb.append("[");
//					sb.append(++index);
//					sb.append("/");
//					sb.append(recCount);
//					sb.append("]");
//					sb.append(" [" + atTradeRecord.lastDateTime.month+ "/" + atTradeRecord.lastDateTime.day + "/" + atTradeRecord.lastDateTime.year + " ");
//					sb.append(atTradeRecord.lastDateTime.hour + ":" + atTradeRecord.lastDateTime.minute + ":" + atTradeRecord.lastDateTime.second + "] ");
//					sb.append("TRADE ");
//					
//					strFormat = "%0." + atTradeRecord.lastPrice.precision + "f";
//					sb.append("  \t[last:" + new PrintfFormat(strFormat).sprintf(atTradeRecord.lastPrice.price));
//					sb.append("  \tlastsize:" + atTradeRecord.lastSize);
//					sb.append("  \tlastexch:" + atTradeRecord.lastExchange.m_atExchangeType);
//					sb.append("  \tcond:" + atTradeRecord.lastCondition[0].m_atTradeConditionType);

			
//					String hour = (atTradeRecord.lastDateTime.hour >= 10) ? String.valueOf(atTradeRecord.lastDateTime.hour) : "0" + atTradeRecord.lastDateTime.hour;
//					String minute = (atTradeRecord.lastDateTime.minute >= 10) ? String.valueOf(atTradeRecord.lastDateTime.minute) : "0" + atTradeRecord.lastDateTime.minute;
//					String second = (atTradeRecord.lastDateTime.second >= 10) ? String.valueOf(atTradeRecord.lastDateTime.second) : "0" + atTradeRecord.lastDateTime.second;
//					String tradeTime = hour + minute + second;

					String curRecord=sb.toString();
					int rtime = atTradeRecord.lastDateTime.hour*10000+atTradeRecord.lastDateTime.minute*100+atTradeRecord.lastDateTime.second;
					if(rtime < 93000){
						state=0;
						premarketTickRecords.add(curRecord);
					}

					else if((rtime>=93000) && (rtime < 160000)){
						if(state==0){
							numRec= 0;
							state=1;
							//System.out.println("state is now 1: "+String.valueOf(rtime));
						}
						if(state == 1){
							//book keep rec and vol
							rec[numRec]=curRecord;
							vol[numRec]=atTradeRecord.lastSize;
							price[numRec]=atTradeRecord.lastPrice.price;
							numRec ++;
							if((atTradeRecord.lastCondition[0].m_atTradeConditionType != 9)&&(rtime<93002)&&(numRec<100)){
								//keep state = 1 and continue
								continue;
							}
							//otherwise, we can change the state to 2
							state = 2;
							int lastPreIndex;
							//this check has to be first as this has higher priority
							if(atTradeRecord.lastCondition[0].m_atTradeConditionType == 9){
								lastPreIndex = numRec-2;
								crossRecVerified=false;
							}
							else{
								//try to find the max index in the vol[i]
							
								int mi = -1;
								long mv = 0;
								for (int i = 0; i < numRec; i++) {
    								if (vol[i] > mv) {
										mv = vol[i];
										mi = i;
									}
								}
								lastPreIndex = mi-1;
								//System.out.println("max index:" + String.valueOf(mi)+"  "+String.valueOf(rtime));
								//if not due to cond=9, no need to verify cross record
								crossRecVerified = true;
							}
							for(int i=0; i<=lastPreIndex; i++)
								premarketTickRecords.add(rec[i]);
							for(int i=lastPreIndex+1; i<numRec; i++)
								marketTickRecords.add(rec[i]);
						}
						//state must be 2
						else{
							if(!crossRecVerified){
								crossRecVerified = true; 
								if ((atTradeRecord.lastSize==vol[numRec]) && 
										(atTradeRecord.lastPrice.price == price[numRec])){
									//skipping this record
									continue;
								}
            					logger.log(Level.SEVERE, this.fetchingSym +": record following cross record does not have same price and size: " +  rec[numRec]);
							}
							marketTickRecords.add(curRecord);
						}
					}
					//rtime > 160000
					else{
						if(state<3){
							state = 3;
							numRec = 0;
						}
						if(state == 3){
							//book keep rec and vol
							rec[numRec]=curRecord;
							vol[numRec]=atTradeRecord.lastSize;
							price[numRec]=atTradeRecord.lastPrice.price;
							numRec ++;
							if((atTradeRecord.lastCondition[0].m_atTradeConditionType != 9)&&(rtime<160500)&&(numRec<100)){
								//keep state = 3 and continue
								continue;
							}
							//otherwise, we can change the state to 4 
							state = 4;
							int lastMarketIndex;
							//this check has to be first as this has higher priority
							if(atTradeRecord.lastCondition[0].m_atTradeConditionType == 9){
								lastMarketIndex = numRec-1;
								crossRecVerified=false;
							}
							else{
								//try to find the max index in the vol[i]
								int mi = -1;
								long mv = 0;
								for (int i = 0; i < numRec; i++) {
    								if (vol[i] > mv) {
										mv = vol[i];
										mi = i;
									}
								}
								lastMarketIndex = mi;
								//if not due to cond=9, no need to verify cross record
								crossRecVerified = true;
							}
							for(int i=0; i<=lastMarketIndex; i++)
								marketTickRecords.add(rec[i]);
							for(int i=lastMarketIndex+1; i<numRec; i++)
								aftermarketTickRecords.add(rec[i]);
						}
						//state must be 4 
						else{
							if(!crossRecVerified){
								crossRecVerified = true; 
								if ((atTradeRecord.lastSize==vol[numRec]) && 
										(atTradeRecord.lastPrice.price == price[numRec])){
									//skipping this record
									continue;
								}
            					logger.log(Level.SEVERE, this.fetchingSym +": record following cross record does not have same price and size: " +  rec[numRec]);
							}
							aftermarketTickRecords.add(curRecord);
						}
					}
					
				//	try {
				//		if (m_fetcher.subtractTime(tradeTime, "093000") < 0) {
				//			premarketTickRecords.add(sb.toString() + "\n");
				//		} else if (atTradeRecord.lastDateTime.hour >= 16) {
				//			aftermarketTickRecords.add(sb.toString() + "\n");
				//		} else {
				//			marketTickRecords.add(sb.toString() + "\n");
				//		}
				//	} catch (ParseException e) {
				//		System.out.println("Time parsing error");
				//	}
				}
				break;
				case ATTickHistoryRecordType.TickHistoryRecordQuote: {
					ATTICKHISTORY_QUOTE_RECORD atQuoteRecord = (ATTICKHISTORY_QUOTE_RECORD) record;
					StringBuilder sb = new StringBuilder();
					sb.append("[");
					sb.append(++index);
					sb.append("/");
					sb.append(recCount);
					sb.append("]");
					sb.append(" [" + atQuoteRecord.quoteDateTime.month + "/" + atQuoteRecord.quoteDateTime.day + "/" + atQuoteRecord.quoteDateTime.year + " ");
					sb.append(atQuoteRecord.quoteDateTime.hour + ":" + atQuoteRecord.quoteDateTime.minute + ":" + atQuoteRecord.quoteDateTime.second + "] ");
					sb.append("QUOTE ");

					strFormat = "%0." + atQuoteRecord.bidPrice.precision + "f";
					sb.append("  \t[bid:" + new PrintfFormat(strFormat).sprintf(atQuoteRecord.bidPrice.price));

					strFormat = "%0." + atQuoteRecord.askPrice.precision + "f";
					sb.append("  \task:" + new PrintfFormat(strFormat).sprintf(atQuoteRecord.askPrice.price) + " ");

					sb.append("  \tbidsize:" + atQuoteRecord.bidSize);
					sb.append("  \tasksize:" + atQuoteRecord.askSize);
					sb.append("  \tbidexch:" + atQuoteRecord.bidExchange.m_atExchangeType);
					sb.append("  \taskexch:" + atQuoteRecord.askExchange.m_atExchangeType);
					sb.append("  \tcond:" + atQuoteRecord.quoteCondition.m_quoteConditionType);
					String hour = (atQuoteRecord.quoteDateTime.hour >= 10) ? String.valueOf(atQuoteRecord.quoteDateTime.hour) : "0" + atQuoteRecord.quoteDateTime.hour;
					String minute = (atQuoteRecord.quoteDateTime.minute >= 10) ? String.valueOf(atQuoteRecord.quoteDateTime.minute) : "0" + atQuoteRecord.quoteDateTime.minute;
					String second = (atQuoteRecord.quoteDateTime.second >= 10) ? String.valueOf(atQuoteRecord.quoteDateTime.second) : "0" + atQuoteRecord.quoteDateTime.second;
					String tradeTime = hour + minute + second;
					try {
						if (m_fetcher.subtractTime(tradeTime, "093000") < 0) {
							premarketTickRecords.add(sb.toString() + "\n");
						} else if (atQuoteRecord.quoteDateTime.hour >= 16) {
							aftermarketTickRecords.add(sb.toString() + "\n");
						} else {
							marketTickRecords.add(sb.toString() + "\n");
						}
					} catch (ParseException e) {
						System.out.println("Time parsing error");
					}
					break;
				}
			}
		}
			
		//if state is 3, we will force it to 4 as there might not be any following tick
		if((state == 3) && (numRec >0)){
			int mi = -1;
			long mv = 0;
			for (int i = 0; i < numRec; i++) {
    			if (vol[i] > mv) {
					mv = vol[i];
					mi = i;
				}
			}
			for(int i=0; i<=mi; i++)
				marketTickRecords.add(rec[i]);
			for(int i=mi+1; i<numRec; i++)
				aftermarketTickRecords.add(rec[i]);
		}
		//if state is 4, but crossRecVerified is still false, that is an error 
		if((state == 4) && (!crossRecVerified)){
           	logger.log(Level.SEVERE, this.fetchingSym +": record following cross record does not have same price and size: " +  rec[numRec]);
		}
		
		if (!premarketTickRecords.isEmpty()) {
			this.writeTickRecord(this.premarketFilePath,premarketTickRecords);
		}
		if (!marketTickRecords.isEmpty()) {
			this.writeTickRecord(this.marketFilePath,marketTickRecords);
		}
		if (!aftermarketTickRecords.isEmpty()) {
			this.writeTickRecord(this.aftermarketFilePath,aftermarketTickRecords);
		}
		System.out.println("--------------------------------------------------------------\nTotal records:" + recCount);
		m_fetcher.onRequestComplete();
	}

	public void OnBarHistoryDbResponse(long origRequest, ATServerAPIDefines.ATBarHistoryResponseType responseType, Vector<ATServerAPIDefines.ATBARHISTORY_RECORD> vecData)
	{
		String strResponseType = "";
		switch(responseType.m_responseType)
		{
			case ATBarHistoryResponseType.BarHistoryResponseSuccess: strResponseType = "BarHistoryResponseSuccess"; break;
			case ATBarHistoryResponseType.BarHistoryResponseInvalidRequest: strResponseType = "BarHistoryResponseInvalidRequest"; break;
			case ATBarHistoryResponseType.BarHistoryResponseMaxLimitReached: strResponseType = "BarHistoryResponseMaxLimitReached"; break;
			case ATBarHistoryResponseType.BarHistoryResponseDenied: strResponseType = "BarHistoryResponseDenied"; break;
			default: break;
		}

		System.out.println("RECV (" + origRequest +"): Bar History response [" + strResponseType + "]\n--------------------------------------------------------------");

		Iterator<ATServerAPIDefines.ATBARHISTORY_RECORD> itrDataItems = vecData.iterator();
		int index = 0;
		int recCount = vecData.size();
		String strFormat = "%0.2f";
		while(itrDataItems.hasNext())
		{
			ATServerAPIDefines.ATBARHISTORY_RECORD record = (ATServerAPIDefines.ATBARHISTORY_RECORD)itrDataItems.next();
			StringBuilder sb = new StringBuilder();
			sb.append((++index) + "/" + recCount + " ");
			sb.append("[" + record.barTime.month + "/" + record.barTime.day + "/" + record.barTime.year + " ");
			sb.append(record.barTime.hour + ":" + record.barTime.minute + ":" + record.barTime.second + "] ");


			strFormat = "%0." + record.open.precision + "f";
			sb.append("  \t[o:" + new PrintfFormat(strFormat).sprintf(record.open.price));

			strFormat = "%0." + record.high.precision + "f";
			sb.append("  \th:" + new PrintfFormat(strFormat).sprintf(record.high.price) + " ");

			strFormat = "%0." + record.low.precision + "f";
			sb.append("  \tl:" + new PrintfFormat(strFormat).sprintf(record.low.price) + " ");

			strFormat = "%0." + record.close.precision + "f";
			sb.append("  \tc:" + new PrintfFormat(strFormat).sprintf(record.close.price) + " ");

			sb.append("  \tvol:" + record.volume);

			System.out.println(sb.toString());
		}
		System.out.println("--------------------------------------------------------------\nTotal records:" + recCount);
	}

	public void OnMarketMoversDbResponse(long origRequest, ATServerAPIDefines.ATMarketMoversDbResponseType responseType, Vector<ATServerAPIDefines.ATMARKET_MOVERS_RECORD> vecData)
	{
		String strResponseType = "";
		switch(responseType.m_responseType)
		{
			case ATMarketMoversDbResponseType.MarketMoversDbResponseSuccess: strResponseType = "MarketMoversDbResponseSuccess"; break;
			case ATMarketMoversDbResponseType.MarketMoversDbResponseInvalidRequest: strResponseType = "MarketMoversDbResponseInvalidRequest"; break;
			case ATMarketMoversDbResponseType.MarketMoversDbResponseDenied: strResponseType = "MarketMoversDbResponseDenied"; break;
			default: break;
		}
		System.out.println("RECV (" + origRequest +"): Market Movers response [ " + strResponseType + "]\n--------------------------------------------------------------");
		Iterator<ATServerAPIDefines.ATMARKET_MOVERS_RECORD> itrMarketMovers = vecData.iterator();
		String strFormat = "";
		while(itrMarketMovers.hasNext())
		{
			ATServerAPIDefines.ATMARKET_MOVERS_RECORD record = (ATServerAPIDefines.ATMARKET_MOVERS_RECORD)itrMarketMovers.next();
			String strSymbol = new String(record.symbol.symbol);
			int plainSymbolIndex = strSymbol.indexOf((byte)0);
			if(plainSymbolIndex > 0)
				strSymbol = strSymbol.substring(0, plainSymbolIndex);

			System.out.println("Market movers symbol: " + strSymbol + "\n------------------\n");
			for(int i = 0; i < record.items.length; i++)
			{
				String strItemSymbol = new String(record.items[i].symbol.symbol);
				int plainItemSymbolIndex = strItemSymbol.indexOf((byte)0);
				if(plainItemSymbolIndex > 0)
					strItemSymbol = strItemSymbol.substring(0, plainItemSymbolIndex);
				else
					strItemSymbol = "";
				

				StringBuilder sb = new StringBuilder();
				sb.append("symbol:");
				sb.append(strItemSymbol);
				
				strFormat = "%0." + record.items[i].lastPrice.precision + "f";
				sb.append("  \tlast:" + new PrintfFormat(strFormat).sprintf(record.items[i].lastPrice.price));
				
				sb.append(" volume:");
				sb.append(record.items[i].volume);

				String strName = new String(record.items[i].name);
				int plainNameIndex = strName.indexOf((byte)0);
				if(plainNameIndex > 0)
					strName = strName.substring(0, plainNameIndex-1);
				else
					strName = "";

				sb.append(" name: " + strName);
				System.out.println(sb.toString());
			}			
		}		
	}

//	public void writeTickRecord(String filepath, ArrayList<String> records)
	public void writeTickRecord(String filepath, ArrayList<String> csvTicks)
	{
//		ArrayList<String> csvTicks = reformatATTick(records);
		try {
            /*
			FileOutputStream dest = new FileOutputStream(filepath);
			ZipOutputStream writer = new ZipOutputStream(new BufferedOutputStream(dest));
			ZipEntry entry = new ZipEntry("data.tsv");
			writer.putNextEntry(entry);
            */
			File data = new File(filepath);
			if (!data.exists()) {
				data.createNewFile();
			}
			FileWriter fw = new FileWriter(data,true);
			BufferedWriter writer = new BufferedWriter(fw);
			for (String record : csvTicks) {
				//writer.write(record.getBytes(), 0, record.length());
                writer.write(record+"\n");
			}
			writer.close();

		} catch (IOException e) {
			System.out.println("Cannot write to file: " + e.getMessage() + e.getStackTrace());
		}
	}

	public ArrayList<String> reformatATTick(ArrayList<String> records) {
		ArrayList<String> new_ticks = new ArrayList<>();
		for (String record : records) {
			ArrayList<String> values = new ArrayList<>(Arrays.asList(record.split("\\s+")));
			if (values.get(3).equals("QUOTE")) {
				continue;
			}
			String[] newValues = new String[7];
			try {
				newValues[0] = values.get(1).replace("[", "");
				newValues[1] = values.get(2).replace("]", "");
				newValues[2] = values.get(3);
				for (int i = 4; i < values.size(); i++) {
					newValues[i - 1] = values.get(i).split(":")[1];
				}
			} catch (Exception e) {
				System.out.println(e.fillInStackTrace());
			}
			String new_tick = "";
			for (String value : newValues) {
				new_tick += value + ",";
			}
			new_tick = new_tick.substring(0, new_tick.length()-1);
			new_ticks.add(new_tick);
		}
		return new_ticks;
	}


	//this is called when fetch data request is received
	public void setOutputPath(String symbol, String premarketFilePath, String marketFilePath, String aftermarketFilePath) {
		this.fetchingSym = symbol;
		this.premarketFilePath = premarketFilePath;
		this.marketFilePath = marketFilePath;
		this.aftermarketFilePath = aftermarketFilePath;

		initSignalFinding();
	}

	public void initSignalFinding(){
		state=0; // can be 0 to 4, see comments in Requestor.java
		numRec=0;
	}
}
