# Import the os module, for the os.walk function
import os, time
import cjson
import savant.fetcher.fetch_attick as fetcher
import savant.ticker as ticker
from savant.config import settings

 

 # Set the directory you want to start from
rootDir = settings.DOWNLOAD_DIR
def generate_secboot_for_existing_trade(report = False):
    for dirName, subdirList, fileList in os.walk(rootDir):
        
        for fname in fileList:
            if "markethours" in fname:
                symbol = fname.split('_')[0]
                sec_bar_path = dirName+"/"+symbol+"_second_bar.csv.gz"
                if os.path.exists(sec_bar_path):
                    continue
                print sec_bar_path
                if not report:
                    ticker.generate_secondbar_from_tradetick(symbol, dirName.split("/")[-1])




def fetch_quotes_for_existing_trade():
    for dirName, subdirList, fileList in os.walk(rootDir):
    #    print('Found directory: %s' % dirName)
        
        for fname in fileList:
            if "201007" in dirName:
                continue
            if "markethours" in fname:
                symbol = fname.split('_')[0]
                quote_data_path = dirName+"/"+symbol+"_quote.csv.gz"
                print quote_data_path
                if os.path.exists(quote_data_path):
                    print "quote data already fetched: ", symbol
                    continue
                
                request = {"command": "get", "symbol": symbol, "date": dirName.split("/")[-1], "gettrade": "false", "getquote": "true"}
                print request 
                try:
                    fetcher_caller = fetcher.FetcherCaller()
                    fetcher_caller.set_request(cjson.encode(request))
                    response = fetcher_caller.send_request()
                    fetcher_caller.close()
                except:
                    print "Unable to send fetch request"
                    continue
                count_down = 60
                while count_down > 0:
                    if os.path.exists(quote_data_path):
                        print "IPO data fetched:", symbol
                        fetched = True
                        time.sleep(5)
                        break
                    time.sleep(1)
                    count_down -= 1
                if not fetched:
                    print "Unable to download data for ", symbol

