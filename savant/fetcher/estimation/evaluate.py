#import os, os.path, sys
import socket
import json
import pdb
import time
import sqlite3
import datetime

from savant.config import settings
from savant.fetcher.fetch_attick import *

def get_data(symbol,date):
    request = {"command":"get","symbol":symbol,"date":date}
    json_request = json.dumps(request)
    caller = FetcherCaller(json_request) 

def check_status():
    request = {"command":"check"}
    json_request = json.dumps(request)
    caller = FetcherCaller(json_request) 
    return caller.stat

def detail(v,vc,vf):
    if v<=0:
        return '0'+vf[0]
    res=''
    for i in range (0,len(vc)):   
        v/=vc[i]
        if v>0:
            vcur=v;
            if i+1<len(vc):
                vcur%=vc[i+1]
            res=str(vcur)+vf[i]+' '+res
            i+=1
        else:
            break
    return res

vc=[[1,60,60,24],[1,1024,1024,1024]]
vf=['SMHD','KMGT']

def queryTable(n):
    logpath='../output.txt'
    if os.path.exists(logpath)==True:
        os.remove(logpath)
    conn = sqlite3.connect("../../data/savant.db")
    curs=conn.cursor()
    curs.execute("select prev_volume, symbol, date_updated from company order by prev_volume desc")
    ds= curs.fetchall()
    if n<1:
        n=1
    step= float(len(ds))/n
    if step<1:
        step=1
    #print step
    ind=0
    cnt=0
    #time.clock()
    starttime = datetime.datetime.now()
    for s in ds:
        if ind==int((cnt*step + (cnt+1) * step)/2):
            print 'sample index',ind
            print s
            sym=s[1]
            dat=s[2].replace('-','')
            get_data(sym,dat)
            cnt+=1
            while 1:
                stat=check_status()
                if stat.find('Idle')>-1:
                    break
                time.sleep(4)     
        ind+=1
    curs.close()
    conn.close()
    print '\nTime / Space Cost Measurement for '+str(n)+' sampling'
    
    endtime = datetime.datetime.now()
    difftime=(endtime-starttime).seconds
    #ti=long(round(time.clock()))
    ti = difftime
    print detail(ti,vc[0],vf[0])
    file = open(logpath)
    sp=long(file.readline())
    print detail(sp,vc[1],vf[1])
    print 'Time / Space Cost Calculation for whole table with '+str(step)+' scaling'
    print detail(long(ti*step),vc[0],vf[0])    
    print detail(long(sp*step),vc[1],vf[1])
    return step

if __name__ == "__main__":
    
    mult=queryTable(long(float(sys.argv[1])))
