import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.*;
import java.lang.*;
import java.io.*;
 

import org.json.*;

//import Data;

public class Streamer 
{
    private static Socket socket;
 
    public static void main(String[] args) 
    {
            //String dir = "/home/jingjing/test/savant-torrey-ranch/data";
            String dir = "/Users/jingjingpeng/savant-torrey-ranch/chihuahua/data";
            
            HashMap<String, ArrayList<ArrayList<Double>>> map = new HashMap<String, ArrayList<ArrayList<Double>>>();
            
            ArrayList<ArrayList<Double>> lRet = new ArrayList<ArrayList<Double>>();
            
            readMap(dir, map);
            System.out.println("ReadMap");
         
         try{
                
                int port = 21000;
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("Server Started and listening to the port 25000");
     
            //Server is running always. This is done using this while(true) loop
            while(true) 
            {
                //Reading the message from the client
                socket = serverSocket.accept();
                InputStream is = socket.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br1 = new BufferedReader(isr);
                
                String jstr = br1.readLine();
		        
		        
                System.out.println("Message received from client is "+jstr);
                
                String returnMessage =  null;
                	
                    JSONObject obj = new JSONObject(jstr);
                	String cmd = obj.getString("command");
                	String cl = obj.getString("client");
                    String sList = obj.getString("symlist").toLowerCase();
                    
                    if(cmd.equals("unsubscribe")){
                        
                        if (map.containsKey(sList)){
                           
                            map.remove(sList);
                            
                            returnMessage = "Removed\n"; 
                       
                        }
                     }else if(cmd.equals("subscribe")){
                            
                            System.out.println("add a new list : "+ sList);
                         if (!map.containsKey(sList)){
                            
                            
                            String fname = sList+".txt"; 
                            System.out.println("add a new list : "+ sList);

                            //readData(fname);
                            
                            map.put(sList, readData(fname));
                            returnMessage = "Added the new list\n"; 
                          
                         }else{
                         
                            System.out.println( sList + "is subscribed");
                            returnMessage = "exist\n"; 
                         }
                    /*        lRet = map.get(sList);
                            
                            System.out.println("map contains list : "+ sList);
                           
                            returnMessage = lRet + "\n";
                    */
                  /*  }else if(cmd.equals("update")){
                            
                    
                            returnMessage = "Command is invalid\n"; 
                    }*/
                    //if(all.contains(sList)){
                   // }else{
                     //   returnMessage = "No Sym\n";
                   // }
 
                //Sending the response back to the client.
                OutputStream os = socket.getOutputStream();
                OutputStreamWriter osw = new OutputStreamWriter(os);
                BufferedWriter bw = new BufferedWriter(osw);
                bw.write(returnMessage);
                System.out.println("Message sent to the client is "+returnMessage);
                bw.flush();
            }
	}
        }catch(FileNotFoundException ex){
                System.out.println("errorMessage:" + ex.getMessage());
        }catch(IOException ix){
                System.out.println("IOException");
        }catch (Exception e) 
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch(Exception e){}
        }
    }


    public static void readMap(String dir, HashMap<String, ArrayList<ArrayList<Double>>> map)
    {    
        try{
                File folder = new File(dir);

                File[] listOfFiles = folder.listFiles();

                for(File file : listOfFiles){
                    
                    if(file.isFile()){
                            
                            String fn = file.getName();
                            String frn = dir + "/"+fn;
                           
                            fn = fn.substring(0, fn.indexOf('.'));
                            map.put(fn, readData(frn));
                             
                      }
                 }
                   
                   
               // }catch(FileNotFoundException ex){
                 //       System.out.println("errorMessage:" + ex.getMessage());
               // }catch(IOException ix){
                 //       System.out.println("IOException");
                }catch (Exception e) 
                {
                    e.printStackTrace();
                }
      }
                   
                   
               public static ArrayList<ArrayList<Double>> readData(String frn){
                

                        
                        String line;

                        ArrayList<ArrayList<Double>> all = new ArrayList<ArrayList<Double>>();

                        String t0 = "9:30:0]";
                        double n = 0;
                        double price = 0;
                        double amount = 0;
                        double iPri = 0;
                        double iAmt = 0;

                    try{
                        
                        BufferedReader br = new BufferedReader(new FileReader(frn));
                        
                        while((line = br.readLine()) != null)
                        {
                            String[] ret = line.split(" ");

                            String[] prc = ret[6].split(":");
                            iPri = Double.parseDouble(prc[1]);

                            String[] amt = ret[8].split(":");
                            iAmt = Double.parseDouble(amt[1]);

                            ArrayList<Double> each = new ArrayList<Double>();

                            if(!ret[2].equals(t0))
                            {
                                each.add(n);
                                each.add(price/amount);
                                each.add(amount);
                                all.add(each);

                                t0 = ret[2];
                                amount = iAmt;
                                n++;

                            }else{
                                price = price + iPri*iAmt;
                                amount = amount + iAmt;
                            }
                        }

                        ArrayList<Double> last = new ArrayList<Double>();
                        
                        last.add(n);
                        last.add(price/amount);
                        last.add(amount);
                        all.add(last);
                        
                /*for (ArrayList<Double> d: all)
                    {
                            System.out.println("each is " + d);
                    }

                 /*   if(map.containsKey("qqq_trade")){
                            ArrayList<ArrayList<Double>> arr = map.get("qqq_trade");
                            for (ArrayList<Double> d: arr)
                            {
                                    System.out.println("each is " + d);
                            }

                    }*/
                            br.close();
            
            }catch(FileNotFoundException ex){
                    System.out.println("errorMessage:" + ex.getMessage());
            }catch(IOException ix){
                    System.out.println("IOException");
            }catch (Exception e) 
            {
                e.printStackTrace();
            }
         
                           return all;
         }



}