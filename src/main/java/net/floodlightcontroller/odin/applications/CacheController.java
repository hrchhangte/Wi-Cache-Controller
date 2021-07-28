package net.floodlightcontroller.odin.applications;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.odin.master.NotificationCallback;
import net.floodlightcontroller.odin.master.NotificationCallbackContext;
import net.floodlightcontroller.odin.master.OdinApplication;
import net.floodlightcontroller.odin.master.OdinClient;
import net.floodlightcontroller.odin.master.OdinEventSubscription;
import net.floodlightcontroller.odin.master.OdinEventSubscription.Relation;
import net.floodlightcontroller.odin.master.OdinMaster.MobilityParams;
import net.floodlightcontroller.util.MACAddress;
import sun.management.resources.agent_fr;
import sun.net.InetAddressCachePolicy;

public class CacheController extends OdinApplication {
	
	protected static Logger log = LoggerFactory.getLogger(CacheController.class);
	private ServerSocket clntLstnSock, agntLstnSock, agntLstnSockData;
	private Socket clntSockMain, agntSockMain, agntSockMainData;	
	private int agntMesgPortLocal = 8000, agntDataPortLocal = 8001, clntMesgPortLocal = 9000;		
	
	//for file transfer update
	private DatagramSocket updateSock;
	private int updatePortLocal = 7000;	
	
	//<Agent ip address> <Socket fd> - data socket key appended by "_data"
	private ConcurrentMap<String, Socket> agntSockMap = new ConcurrentHashMap<String, Socket>();
	
	//<Client ip address> <File transfer update parameters>
	private ConcurrentMap<String, UpdateParam> fileTransfrMap = new ConcurrentHashMap<String, UpdateParam>();
	
	//<Agent ip address> <Map : <File name> <File Size>> 
	private ConcurrentMap<String, ConcurrentMap<String, Long>> agntFileMap = new ConcurrentHashMap<String, ConcurrentMap<String, Long>>();
	
	// <Client ip address> <Set of reachable Agent ip addresses>
	private ConcurrentMap<String, Set<String>> clntAgntMap = new ConcurrentHashMap<String, Set<String>>();
	
	// <Agent ip address> <No. of active file transfers>
	private ConcurrentMap<String, Integer> fileTransfrCount = new ConcurrentHashMap<String, Integer>();
	
	@Override
	public void run() {
		// TODO Auto-generated method stub		
		try {
			
			//create agent-file mapping hash table by reading file
			makeAgntFileMap();
			
			//start a thread which listens for connection requests from agents
			handleAgent();	
			
			//start a thread to update client-agent mapping - satisfying signal and noise threshold
			//updateRxStats();			
			
			//simulate mobility of a single client by taking user input
			simMobility();						
			
			//start a thread to receive file transfer status from agents
			handleUpdate();
			
			//start a thread to handle clients' movements
			//handleMobility();			
			
			//listen for incoming requests from clients
			handleClient();
			
		}catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	
	}
	
	public void reAssign(){	

		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				
				while(true){
					
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					HashSet<OdinClient> clients = new HashSet<OdinClient>(getClients());
					for (OdinClient oc: clients) {
						//assign to the current agent
						if(oc.getIpAddress().getHostAddress().equals("0.0.0.0")){
							handoffClientToAp(oc.getMacAddress(), oc.getLvap().getAgent().getIpAddress());
						}
					}
				}
			}
		};
		new Thread(thread).start();
	}
	
	public void updateRxStats(){
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
						
						//start after 5 seconds
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
		
						double SIGNAL_THRESHOLD = -56;
													
						while(true){
							
							//do update this every 1 second(s)
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						
							//get the clients
							HashSet<OdinClient> clients = new HashSet<OdinClient>(getClients());
							
							/* for each of the agents defined in the Poolfile (APs)*/
							for (InetAddress agentAddr: getAgents()) {
								/* FIXME: if the next line is run before the APs are activated,
								*the program blocks here */
								Map<MACAddress, Map<String, String>> vals = getRxStatsFromAgent(agentAddr);
								
								/* for each STA which has contacted that agent (AP) (not necessarily associated) */
								for (Entry<MACAddress, Map<String, String>> vals_entry: vals.entrySet()) {
									
									MACAddress staHwAddr = vals_entry.getKey();		
									
									/* for all the clients registered in Odin (those who have an LVAP) */
									for (OdinClient oc: clients) {
										/* 
										* Check four conditions:
										* - the MAC address of the client must be that of the connected STA
										* - the IP address of the STA cannot be null
										* - the IP address of the STA cannot be 0.0.0.0
										* - the received signal must be over the threshold
										*/
										if (oc.getMacAddress().equals(staHwAddr)
												&& oc.getIpAddress() != null
												&& !oc.getIpAddress().getHostAddress().equals("0.0.0.0")
												&& Double.parseDouble(vals_entry.getValue().get("avg_signal")) >= SIGNAL_THRESHOLD){
																							
											//log.info("\t"+oc.getIpAddress().getHostAddress()+" : " +Double.parseDouble(vals_entry.getValue().get("avg_signal"))
											//+"dBm, Agent : "+agentAddr.getHostAddress());
											
											//get the ip address of the client in string format
											String staIpAddr = oc.getIpAddress().getHostAddress();
											
											Set<String> agntSet = new HashSet<String>();
											
											//first time adding an entry for the client
											if(!clntAgntMap.containsKey(staIpAddr))
												clntAgntMap.put(staIpAddr, agntSet);
											
											//else add to set, add set to map
											agntSet = clntAgntMap.get(staIpAddr);
											agntSet.add(agentAddr.getHostAddress());
											clntAgntMap.put(staIpAddr, agntSet);					
											
										}
									}
								}
							}
						}//while
				
				}catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};	
		
		new Thread(thread).start();
	}
	
	public String selectAgnt(String clntAddr){		
		
		//this returns every client having LVAP, may not be associated, connected anymore
		HashSet<OdinClient> clients = new HashSet<OdinClient>(getClients());
		
		//get the number of clients connected to each agents
		
		//<Agent ip address> <No. of clients connected>
		ConcurrentMap<String, Integer> connCount = new ConcurrentHashMap<String, Integer>();
		for (OdinClient oc: clients) {
			
			if(oc.getIpAddress() != null 
					&& !oc.getIpAddress().getHostAddress().equals("0.0.0.0")){
				
				String curAgntAddr = oc.getLvap().getAgent().getIpAddress().getHostAddress();
				if(!connCount.containsKey(curAgntAddr)){
					connCount.put(curAgntAddr, 1);
				}
				else
					connCount.put(curAgntAddr, connCount.get(curAgntAddr)+1);	
			}
		}
		
		//get the agent ip set satisfying the content and signal threshold for the client
		Set<String> agntSet = clntAgntMap.get(clntAddr);
		
		
		//select the best agent for the client
			
		String bestAgnt = null;
		int minTransfrs = 0;
		int minClients = 0;
		
		for(String curAgnt : agntSet){
			
			if(bestAgnt == null){
				
				bestAgnt = curAgnt;
				minTransfrs = fileTransfrCount.get(curAgnt);
				minClients = connCount.get(curAgnt);
			
			}				
			
			//compare active transfers
			if(fileTransfrCount.get(curAgnt)<minTransfrs){
				bestAgnt = curAgnt;
				minTransfrs = fileTransfrCount.get(curAgnt);
				minClients = connCount.get(curAgnt);
			}
			
			else if(fileTransfrCount.get(curAgnt)==minTransfrs){
				//compare clients connected if active transfers are equal
				if(connCount.get(curAgnt)<minClients){
					bestAgnt = curAgnt;
					minTransfrs = fileTransfrCount.get(curAgnt);
					minClients = connCount.get(curAgnt);
				}
			}
					
						
		}//for
		
		
		//returns the selected agent ip address, can be null, should be checked by caller
		return bestAgnt;		
	}
	
	public void simMobility(){
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {	
					
					try {
						Thread.sleep(30000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		
					log.info("Mobility simulation started");
						
					while(true){
						
						String newAgntAddr = null;					
						BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
						 									
						HashSet<OdinClient> clients = new HashSet<OdinClient>(getClients());
						
						String clntAddr = "192.168.1.110";
						
						//HashSet<OdinClient> clientList = new HashSet<OdinClient>(getClients());						
												
						Iterator<OdinClient> clientIter = clients.iterator();
						OdinClient oc = null;
						while(clientIter.hasNext()){
							oc = clientIter.next();  
							if(oc.getIpAddress().getHostAddress().equals(clntAddr)){
								
								log.info("Client object found!");
								
								break;
							}
						}
						
						
						//OdinClient oc = getClientFromHwAddress(MACAddress.valueOf("54:35:30:D6:A4:D3"));
						//String clntAddr = oc.getIpAddress().getHostAddress();
						
						//get user input
						input.readLine();	
						
						try {
							Thread.sleep(20000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						
						//for (OdinClient oc: clients) {
							
							//not currently associated, ignore
							//if(oc.getIpAddress() == null 
								//	|| oc.getIpAddress().getHostAddress().equals("0.0.0.0")){
									//add client not to track here
								//continue;
							//}
						
							//client ip to track
							//String clntAddr = oc.getIpAddress().getHostAddress();
							
							//get the odin client object for the ip
							//while(true){								
															
								//check if there is current file transfer
								if(fileTransfrMap.containsKey(clntAddr)){
									
									//check if the current status is false
									if(fileTransfrMap.get(clntAddr).finStat==false){
										
										log.info("File transfer entry currently exists for the client : "+clntAddr);										
										
										//send handover mesg to current agent
										log.info(oc.getLvap().getAgent().getIpAddress().getHostAddress());
										
										Socket agntSock = agntSockMap.get(oc.getLvap().getAgent().getIpAddress().getHostAddress());
										PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());										
										agntWrtr.write("CLNT_HANDOVER"+" "+clntAddr+" ");
										
										log.info("CLNT_HANDOVER"+" "+clntAddr+" ");
										
										agntWrtr.flush();
										
										/*
										
										//get update from agent 
										BufferedReader agntMesgRdr = new BufferedReader(new InputStreamReader(agntSock.getInputStream()));
										String mesg = agntMesgRdr.readLine();
										log.info(mesg);
										
										//update transfer status with the current agent
										String[] tokens = mesg.split("\\s+");
										fileTransfrMap.put(tokens[1], new UpdateParam(tokens[2], agntSock.getInetAddress().getHostAddress(), 
												tokens[3], tokens[4], tokens[5]));
										*/
										
										//switch between the agents
										String agntAddr = oc.getLvap().getAgent().getIpAddress().getHostAddress();
										if(agntAddr.equals("192.168.1.10"))
											newAgntAddr = "192.168.1.12";
										else if(agntAddr.equals("192.168.1.12"))
											newAgntAddr = "192.168.1.10";
										
										//handoff
										InetAddress newAgntInetAddr = InetAddress.getByName(newAgntAddr);
										
										log.info("Handoff due to mobility...");
										
										handoffClientToAp(oc.getMacAddress(), newAgntInetAddr);
										
										log.info("Handoff completed");
										
										//subtract file transfer count of previous agent
										fileTransfrCount.put(agntAddr, fileTransfrCount.get(agntAddr)-1);
										
										//check file availability in the new agent
										Map<String, Long> nameSizeMap = agntFileMap.get(newAgntAddr);
										if(nameSizeMap.containsKey(fileTransfrMap.get(clntAddr).fileName)){
										
											//instruct the new agent to send the remaining parts of file
											agntSock = agntSockMap.get(newAgntAddr);
											agntWrtr = new PrintWriter(agntSock.getOutputStream());	
											
											//details sent here does not really matter,the new agent gets it from client
											agntWrtr.write("SEND_FILE" +" "+clntAddr+" "+fileTransfrMap.get(clntAddr).fileName+" "
											+fileTransfrMap.get(clntAddr).fileSize+" "+"1"+" "+"CACHED"+" ");
											
											log.info("SEND_FILE" +" "+clntAddr+" "+fileTransfrMap.get(clntAddr).fileName+" "
													+fileTransfrMap.get(clntAddr).fileSize+" "+"1"+" "+"CACHED"+" ");
											
											agntWrtr.flush();	
											
											//update transfer status with the new agent
											fileTransfrMap.put(clntAddr, new UpdateParam(false, newAgntAddr, 
													fileTransfrMap.get(clntAddr).fileName, fileTransfrMap.get(clntAddr).fileSize, 1 ));
											
											//add file transfer count of new agent
											//first time
											if(!fileTransfrCount.containsKey(newAgntAddr))
												fileTransfrCount.putIfAbsent(newAgntAddr, 1);
											//add the count
											else
											fileTransfrCount.put(newAgntAddr, fileTransfrCount.get(newAgntAddr)+1);
											
											//print file transfer status table
											printFileTransfrMap();
											
										}
										else{
											
											//fetchAndSend(tokens[3], Long.parseLong(tokens[4]), Long.parseLong(tokens[5]), oc);						
											
											//data structure update taken care by the called function
											
										}										
									
									}							
								}//if	
								
								
								
								
					}
						
					}catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};	
		new Thread(thread).start();	
	}
	
	public void makeAgntFileMap() throws Exception{		
		

		log.info("Creating Agent-File mapping table");
		BufferedReader agntRdr = new BufferedReader(new FileReader("/home/zela/FileStorage/" +"agents"));
		String agnts = null;
		Set<String> agntsSet = new HashSet<String>();
		while((agnts = agntRdr.readLine())!=null){
			agntsSet.add(agnts);
		}
		
		//close the file
		agntRdr.close();								
				
		ConcurrentMap<String, Long> fileSizeMap = null;
									
		for(String curAgntAddr : agntsSet){
			
			BufferedReader lineRdr = new BufferedReader(new FileReader("/home/zela/FileStorage/" +curAgntAddr));
			String line = null;
			while ((line = lineRdr.readLine()) != null) {
				
				String fileName = line;
				Long fileSize = new File("/home/zela/FileStorage/" + fileName).length();
				
				if(!agntFileMap.containsKey(curAgntAddr)){
					fileSizeMap = new ConcurrentHashMap<String, Long>();
					fileSizeMap.put(fileName, fileSize);
					agntFileMap.put(curAgntAddr, fileSizeMap);
				}	
				
				fileSizeMap = agntFileMap.get(curAgntAddr);
				fileSizeMap.put(fileName, fileSize);
				agntFileMap.put(curAgntAddr, fileSizeMap);
				
			}
		
			lineRdr.close();
		}
		log.info("Agent-file mapping hash table created successfully!");
		
		printAgntMapTable(agntsSet);
		
	}
	
	public void printAgntMapTable(Set<String> agntsSet){
				
		log.info("Printing agent-file mapping hash table");
		for(String curAgntAddr : agntsSet){
			log.info("Agent : "+curAgntAddr);
			ConcurrentMap<String, Long> fileSizeMap = agntFileMap.get(curAgntAddr);
			for(String key : fileSizeMap.keySet()){
				log.info("\t"+key.toString()+" : "+fileSizeMap.get(key).toString());
			}
		}		
		
	}
	
	public void handleAgent(){
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					log.info("Agent listener thread started");
					agntLstnSock = new ServerSocket(agntMesgPortLocal);	
					agntLstnSockData = new ServerSocket(agntDataPortLocal);
										
					while(true){
						log.info("Waiting for connection from agents");
						agntSockMain = agntLstnSock.accept();						
						agntSockMainData = agntLstnSockData.accept();
						
						log.info("New Connection accepted from agent : "+agntSockMain.getInetAddress());
						
						//create an entry in agent-socket mapping hash-table, replaces any previous entry
						agntSockMap.put(agntSockMain.getInetAddress().getHostAddress(), agntSockMain);
						agntSockMap.put(agntSockMain.getInetAddress().getHostAddress()+"_data", agntSockMainData);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};	
		new Thread(thread).start();			
	}
	
	public class UpdateParam{
		
		boolean finStat;
		String agntIp;
		String fileName;
		long fileSize;
		long bytesSent;
		
		public UpdateParam(String finStat, String agntIp, String fileName, String fileSize, String bytesSent){
			
			if(finStat.equals("true"))
				this.finStat = true;
			else if(finStat.equals("false"))
				this.finStat = false;
			this.agntIp = agntIp;
			this.fileName = fileName;
			this.fileSize = Long.parseLong(fileSize);
			this.bytesSent = Long.parseLong(bytesSent);
		}
		
		public UpdateParam(boolean finStat, String agntIp, String fileName, long fileSize, long bytesSent) {
			// TODO Auto-generated constructor stub
			
			this.finStat = finStat;
			this.agntIp = agntIp;
			this.fileName = fileName;
			this.fileSize = fileSize;
			this.bytesSent = bytesSent;
		}
		
	}
	
	public void handleUpdate(){
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {					
						
					log.info("File transfer update listener thread started");
					updateSock = new DatagramSocket(updatePortLocal);
					byte[] updateData = new byte[4096];
					DatagramPacket updatePackt = new DatagramPacket(updateData, updateData.length);
					
					while(true){
						
						updateSock.receive(updatePackt);
						String updateStr = new String(updatePackt.getData());
						log.info("UDP UPDATE : "+updateStr);	
						
						//update format : TRNSFR_STAT <client ip> <fin status = true/false> <file name> <file size> <bytes sent>
						String[] tokens = updateStr.split("\\s+");	
						
						//update file transfer status hash-table
						fileTransfrMap.put(tokens[1], new UpdateParam(tokens[2], updatePackt.getAddress().getHostAddress(), 
								tokens[3], tokens[4], tokens[5]));	
						
						log.info("UDP UPDATE : "+fileTransfrMap.get(tokens[1]).finStat+" "+fileTransfrMap.get(tokens[1]).agntIp
								+" "+fileTransfrMap.get(tokens[1]).fileName+" "+fileTransfrMap.get(tokens[1]).fileSize+" "+
								fileTransfrMap.get(tokens[1]).bytesSent);
						
						//if finished, subtract count
						if(fileTransfrMap.get(tokens[1]).finStat==true)
							fileTransfrCount.put(fileTransfrMap.get(tokens[1]).agntIp, (fileTransfrCount.get(fileTransfrMap.get(tokens[1]).agntIp))-1);
						
						//print file transfer status table
						printFileTransfrMap();
						
						//remove file transfer entry if finished
						if(fileTransfrMap.get(tokens[1]).finStat==true)
							fileTransfrMap.remove(tokens[1]);
						
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};	
		new Thread(thread).start();		
	}	
	
	public void printFileTransfrMap(){
		
		log.info("Printing file transfer map status : ");
		for(Map.Entry<String, UpdateParam> entry : fileTransfrMap.entrySet()){
			
			log.info("\t"+entry.getKey()+" : ");			
			log.info("\t\t"+entry.getValue().agntIp);
			log.info("\t\t"+entry.getValue().fileName);
			log.info("\t\t"+entry.getValue().fileSize);
			log.info("\t\t"+entry.getValue().bytesSent);
			log.info("\t\t"+entry.getValue().finStat);
			
		}		
		
		//always call printFileTransCount()
		printFileTransCount();
	}
	
	public void printFileTransCount(){
		
		log.info("Printing file transfer count : ");
		
		for(Map.Entry<String, Integer> entry : fileTransfrCount.entrySet()){
			
			log.info("\t"+entry.getKey()+" : "+entry.getValue());
			
		}
		
		
	}
	
	public void handleClient() throws IOException, InterruptedException{
		
		//create socket for client connections
		clntLstnSock = new ServerSocket(clntMesgPortLocal);			
			
		//print current client-agent mapping status after every connection
		while(true){
			
			
			//get client list and agent list
			HashSet<OdinClient> clientList = new HashSet<OdinClient>(getClients());
						
			log.info("Current Client-Agent Mapping Status");
			
			Iterator<OdinClient> iter = clientList.iterator();
			OdinClient clntObj = null;
			while(iter.hasNext()){				
				clntObj = iter.next();
				if(!clntObj.getIpAddress().getHostAddress().equals("0.0.0.0") && clntObj.getIpAddress()!=null){
					
					log.info("Client : "+clntObj.getIpAddress()+" LVAP : "+clntObj.getLvap().getBssid()+" Agent : "+clntObj.getLvap().getAgent().getIpAddress());
				}
			}		
						
			log.info("Waiting for connection from clients");
			
			clntSockMain = clntLstnSock.accept();			
						
			//call startClntThread() to start a thread which serves the client
			startClntThread(clntSockMain);
		}//while(true)
		
	}//handleClient()
	
	public void startClntThread(Socket mesgSock){
		
		//anonymous thread handles client requests
		Runnable thread = new Runnable() {
			
			@Override
			public void run() {
				
				try
				{
				// TODO Auto-generated method stub	
				String clntAddr = mesgSock.getInetAddress().getHostAddress();	
							
				log.info("Anonymous thread for client "+clntAddr+" started");
				
				//remove any previous file transfer entry for the client, one request per client at any time
				if(fileTransfrMap.containsKey(clntAddr)){
					fileTransfrMap.remove(clntAddr);
				}	
				
				BufferedReader clntMesgRdr = new BufferedReader(new InputStreamReader(mesgSock.getInputStream()));
				//while(clientMesgRd.read()!=-1 || clientMesgRd.readLine()!=null){
				while(true){
					//read request/message from client
					//message format : FILE_REQ filename or CONN_REQ
					String clntMesg = clntMesgRdr.readLine();
					log.info("Mesg. received from client "+clntAddr+" : "+clntMesg);
					
					//split the message to tokens
					String[] tokens = clntMesg.split("\\s+");
					
					//check the request from client
					switch (tokens[0]) {
					//connection request from client, not needed now
					case "CONN_REQ":
						//log.info("case hit : CONN_REQ");
						break;
						
					//file request from client
					case "FILE_REQ":
						//log.info("case hit : FILE_REQ");
						
						String fileName = tokens[1];					
						sendFileToClnt(mesgSock, fileName);
								
						break;//break for case
					}//switch
					
				}//while(true)
		
				}catch(Exception e){
					e.printStackTrace();
				}
				
			}//run()
			
		};//Runnable()
		new Thread(thread).start();		
	}
	
	public void sendFileToClnt(Socket mesgSock, String fileName) throws IOException{
		
		String clntAddr = mesgSock.getInetAddress().getHostAddress();	
		
		//we have to fetch the latest info of clients and agents
		HashSet<OdinClient> clientList = new HashSet<OdinClient>(getClients());
		HashSet<InetAddress> agentList = new HashSet<InetAddress>(getAgents());;
		
		//get the client object from client list
		Iterator<OdinClient> clientIter = clientList.iterator();
		OdinClient clntObj = null;
		while(clientIter.hasNext()){
			clntObj = clientIter.next();
			if(clntObj.getIpAddress().getHostAddress().equals(clntAddr)){
				
				log.info("Client object for client found!");
				break;
			}
		} 
		String agntAddr = clntObj.getLvap().getAgent().getIpAddress().getHostAddress();
		String newAgntAddr = null;
		
		//check the status of requested file
		boolean curAgentHas = false;
		boolean fileExists = false;
		long fileSize = 0;
				
		ConcurrentMap<String, Long> agntMap = agntFileMap.get(agntAddr);
		if(agntMap.containsKey(fileName)){
			
			//should check the signal and current file downloads threshold is met by current agent
			log.info("Current agent has the file");
			fileExists = true;
			curAgentHas = true;
			fileSize = agntMap.get(fileName);
		}
		else{
			
			log.info("Current agent does not contain the file");
			
			for(Map.Entry<String, ConcurrentMap<String, Long>> entry : agntFileMap.entrySet()){
				
				String agentKey = entry.getKey();
				
				ConcurrentMap<String, Long> fileMap = entry.getValue();
				
				if(fileMap.containsKey(fileName)){
					
					//should check the signal and current file downloads threshold is met by this agent
					log.info("Another agent has the file : "+agentKey);
					fileExists = true;
					curAgentHas = false;
					newAgntAddr = agentKey;
					fileSize = fileMap.get(fileName);
					break;
					
				}
				
			}
			
		}
		
		/*
		//file index is the agent's ip address
		String index = agntAddr;	
		
		BufferedReader indexRdr = new BufferedReader(new FileReader("/home/zela/FileStorage/" +index));		
		
		String line = null;
		while ((line = indexRdr.readLine()) != null) {
			if (fileName.equals(line)) {
				fileExists = true;
				break;
			}
		}
		indexRdr.close();
		*/
		
		//get the file size of requested file
		//long fileSize = new File("/home/zela/FileStorage/" + fileName).length();			 
				
		//file exists at the client's agent
		if (fileExists && curAgentHas) {
			//log.info("File exists at the current agent : "+agntAddr);
			
			//retrieve the socket handle for the agent
			Socket agntSock = agntSockMap.get(agntAddr);
			PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());								
			agntWrtr.write("SEND_FILE" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+"0"+" "+"CACHED"+" ");
			
			log.info("Mesg. : SEND_FILE" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+"0"+" "+"CACHED "+" ");
			
			agntWrtr.flush();							
			
			
			//update file transfer status table
			fileTransfrMap.put(clntAddr, new UpdateParam(false, agntAddr, fileName, fileSize, 0));
			
			//update file transfer count table
			//first time
			if(!fileTransfrCount.containsKey(agntAddr)){
				fileTransfrCount.put(agntAddr, 1);
			}			
			//add the no. of file transfers
			else{
				fileTransfrCount.put(agntAddr, fileTransfrCount.get(agntAddr)+1);
			}
			
			//print file transfer map
			printFileTransfrMap();
			
		} 
		
		/*
		//file absent at the client's agent
		else {
			log.info("File missing at the current agent : "+agntAddr);
			
			//find an agent which has the file
			Iterator<InetAddress> agentIter = agentList.iterator();
			InetAddress newAgntInetAddr = null;
			index = null;
			while(agentIter.hasNext()){
				newAgntInetAddr = agentIter.next();
				//check on the agents which is not the client's agent
				if(!newAgntInetAddr.getHostAddress().equals(agntAddr)){
					index = newAgntInetAddr.getHostAddress();
					line = null;
					fileExists = false;
					indexRdr = new BufferedReader(new FileReader("/home/zela/FileStorage/" +index));
					while ((line = indexRdr.readLine()) != null) {
						if (fileName.equals(line)) {
							fileExists = true;
							break;
						}
					}
					if(fileExists)
						break;
				}
			}
			*/
		
			//file exists at another agent
		else if(fileExists && !curAgentHas){								
				//log.info("File exists at another agent : "+newAgntInetAddr.getHostAddress());
				
				log.info("Handoff due to missing file..."); 				
				handoffClientToAp(clntObj.getMacAddress(), InetAddress.getByName(newAgntAddr)); 
				/*
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
				*/	
				
				while(!clntObj.getLvap().getAgent().getIpAddress().equals(InetAddress.getByName(newAgntAddr))){
					continue;
				}
				log.info("Handoff completed");
								
				//retrieve the socket handle for the agent
				//String newAgntAddr = clntObj.getLvap().getAgent().getIpAddress().getHostAddress();
				Socket agntSock = agntSockMap.get(newAgntAddr);
				PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());									
				agntWrtr.write("SEND_SEG" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+"0"+" "+"CACHED"+" ");
				
				log.info("Mesg. : SEND_SEG" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+"0"+" "+"CACHED"+" ");
				
				agntWrtr.flush();		
				
				//update file transfer status table
				fileTransfrMap.put(clntAddr, new UpdateParam(false, newAgntAddr, 
						fileName, fileSize, 0));
				
				//update file transfer count table
				//first time
				if(!fileTransfrCount.containsKey(newAgntAddr)){
					fileTransfrCount.put(newAgntAddr, 1);
				}
				//add the no. of file transfers
				else{
					fileTransfrCount.put(newAgntAddr, fileTransfrCount.get(newAgntAddr)+1);
				}
				
				//print file transfer map
				printFileTransfrMap();
				
			}								
			/*
			//file does not exist on any agent
				log.info("File missing at all agents");
				
				//call fettchAndSend 
				fileSize = new File("/home/zela/FileStorage/" + fileName).length();
				fetchAndSend(fileName, fileSize, 0, clntObj);
				
																
			}//else
			*/
		//}//else		
		
	}
	
	public void fetchAndSend(String fileName, long fileSize, long bytesSent, OdinClient clntObj) throws IOException{
		
		String clntAddr = clntObj.getIpAddress().getHostAddress();
		String agntAddr = clntObj.getLvap().getAgent().getIpAddress().getHostAddress();
		
		//get file from a remote file server or locally, transfer to current agent			
		log.info("Fetching from remote file server");
		
		Socket agntSock = agntSockMap.get(clntObj.getLvap().getAgent().getIpAddress().getHostAddress());				
		PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());									
		agntWrtr.write("SEND_FILE" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+bytesSent+" "+"NOT_CACHED"+" ");
		
		log.info("Mesg. : SEND_FILE" +" "+clntAddr+" "+fileName+" "+Long.toString(fileSize)+" "+bytesSent+" "+"NOT_CACHED"+" ");
		
		agntWrtr.flush();		
		
		//update file transfer status table
		fileTransfrMap.put(clntAddr, new UpdateParam(false, clntObj.getLvap().getAgent().getIpAddress().getHostAddress(), 
				fileName, fileSize, bytesSent));
		
		//update file transfer count table
		//first time
		if(!fileTransfrCount.containsKey(agntAddr)){
			fileTransfrCount.put(agntAddr, 1);
		}		
		//add the no. of file transfers
		else
		fileTransfrCount.put(agntAddr, fileTransfrCount.get(agntAddr)+1);
		
		//print file transfer map
		printFileTransfrMap();
		
		//send file to client
		log.info("Sending requested file to agent : "+agntAddr);
		
		//get the data socket, not the control socket for the agent - append '_data'
		Socket agntSockData = agntSockMap.get(clntObj.getLvap().getAgent().getIpAddress().getHostAddress()+"_data");
		DataOutputStream dataSndStrm = new DataOutputStream(agntSockData.getOutputStream());
		FileInputStream fileStrm = new FileInputStream(new File("/home/zela/FileStorage/" + fileName));	
										
		/*
		//switch from current agent before sending the file - this is for testing purpose
		Iterator<InetAddress> iterAgent = agentList.iterator();
		InetAddress agentTemp = null;
		while(iterAgent.hasNext()){
			agentTemp = iterAgent.next();
			if(!oc.getLvap().getAgent().getIpAddress().equals(agentTemp)){
				log.info("Switching from "+oc.getLvap().getAgent().getIpAddress()+" to "+agentTemp);
				handoffClientToAp(oc.getMacAddress(), agentTemp);	
				//handoffClientToAp(oc.getMacAddress(), agentTemp);
				
				break;
			}
		}	
					
		*/				
		int read = 0;
		long total = 0;			
		byte[] buffer = new byte[4096];	
		long remaining = fileSize - bytesSent;			
		
		//skip bytes
		fileStrm.skip(bytesSent);
		while ((read = fileStrm.read(buffer,0, (int)Math.min(remaining,buffer.length)))>0) {
			
			dataSndStrm.write(buffer,0,read);
			total += read;
			
			//don't usually use flush with streams
			dataSndStrm.flush();							
		}					
		
		//file sent completely
		if(remaining<=0){
			
			log.info("File transfer complete!!");
			
			//update file transfer status table
			fileTransfrMap.put(clntAddr, new UpdateParam(true, clntObj.getLvap().getAgent().getIpAddress().getHostAddress(), 
					fileName, fileSize, bytesSent));		
			
			//subtract the no. of file transfers
			fileTransfrCount.put(agntAddr, fileTransfrCount.get(agntAddr)-1);			
			
			//print file transfer map
			printFileTransfrMap();
			
			//remove entry from file transfer status table
			fileTransfrMap.remove(clntAddr);
		}
		
		log.info("Total bytes sent to agent "+agntAddr+" : "+total);
		
		fileStrm.close();
		
	}
	
	public void handleMobility(){
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
								
				class MobilityManagerCache{
					
					/* A table including each client and its mobility statistics */
					private ConcurrentMap<MACAddress, MobilityStats> clientMap = new ConcurrentHashMap<MACAddress, MobilityStats> ();
					private final String STA; 						// Handle a mac or all STAs ("*")
					private final String VALUE; 					// Parameter to measure (signal, noise, rate, etc.)
					private boolean scan; 							// For testing only once
					private MobilityParams MOBILITY_PARAMS;			// Mobility parameters imported from poolfile

					MobilityManagerCache () {
						/*this.STA = "40:A5:EF:05:9B:A0";*/
						this.STA = "*";
						this.VALUE = "signal";
						this.scan = true;
					}
					
					
					/**anObject
					 * Condition for a hand off
					 *
					 * Example of params in poolfile imported in MOBILITY_PARAMS:
					 *
					 * MOBILITY_PARAMS.HYSTERESIS_THRESHOLD = 15000;
					 * MOBILITY_PARAMS.SIGNAL_THRESHOLD = 200;
					 * MOBILITY_PARAMS.NUMBER_OF_TRIGGERS = 5;  
					 * MOBILITY_PARAMS.TIME_RESET_TRIGGER = 1000;
					 *
					 * With these parameters a hand off will start when:
					 *
					 * At least 5 packets below 200 (-56dBm) have been received by a client during 1000 ms, and a previous hand off has not happened in the last 15000 ms
					 *
					 */

					/**
					 * Register subscriptions
					 */
					private void init () {
						OdinEventSubscription oes = new OdinEventSubscription();
						/* FIXME: Add something in order to subscribe more than one STA */
						oes.setSubscription(this.STA, this.VALUE, Relation.LESSER_THAN, this.MOBILITY_PARAMS.signal_threshold); 
						NotificationCallback cb = new NotificationCallback() {
							@Override
							public void exec(OdinEventSubscription oes, NotificationCallbackContext cntx) {
								if (scan == true) // For testing only once
									handler(oes, cntx);
							}
						};
						/* Before executing this line, make sure the agents declared in poolfile are started */	
						registerSubscription(oes, cb);
						
						//log.info("MobilityManager: register");  
					}

					private void start() {

						this.MOBILITY_PARAMS = getMobilityParams ();
						/* When the application runs, you need some time to start the agents */
						this.giveTime(this.MOBILITY_PARAMS.time_to_start);
						//this.channelAssignment();
						//this.giveTime(10000);
						//setAgentTimeout(10000); 
						init (); 
					}
					
					/**
					 * This method will handoff a client in the event of its
					 * agent having failed.
					 *
					 * @param oes
					 * @param cntx
					 */
					private void handler (OdinEventSubscription oes, NotificationCallbackContext cntx) {
						OdinClient client = getClientFromHwAddress(cntx.clientHwAddress);
						long lastScanningResult = 0;
						long greaterscanningresult = 0;
						
						double client_signal = 0.0;
						long client_signal_dBm = 0;
						double client_average = 0.0;
						long client_average_dBm = 0;
						int client_triggers = 0;
						
						/*
						log.info("\n*\n*\n*\n*\n*\n*");
						log.info("MobilityManager: publish received from " + cntx.clientHwAddress
				                                        + " in agent " + cntx.agent.getIpAddress());*/

						/* The client is not registered in Odin, exit */
						if (client == null)
							return;
						long currentTimestamp = System.currentTimeMillis();
						// Assign mobility stats object if not already done
						// add an entry in the clientMap table for this client MAC
						// put the statistics in the table: value of the parameter, timestamp, timestamp, agent, scanning result, average power and number of triggers
						if (!clientMap.containsKey(cntx.clientHwAddress)) {
							clientMap.put(cntx.clientHwAddress, new MobilityStats(cntx.value, currentTimestamp, currentTimestamp, cntx.agent.getIpAddress(), cntx.value, cntx.client_average, cntx.client_triggers));
						}
						else clientMap.put(cntx.clientHwAddress, new MobilityStats(cntx.value, currentTimestamp, clientMap.get(cntx.clientHwAddress).assignmentTimestamp, cntx.agent.getIpAddress(), clientMap.get(cntx.clientHwAddress).scanningResult, clientMap.get(cntx.clientHwAddress).client_average, clientMap.get(cntx.clientHwAddress).client_triggers));
							 
						// get the statistics of that client
						MobilityStats stats = clientMap.get(cntx.clientHwAddress);
								
						/* Now, handoff */
						
						// The client is associated to Odin (it has an LVAP), but it does not have an associated agent
						// If client hasn't been assigned an agent, associate it to the current AP
						if (client.getLvap().getAgent() == null) {
							log.info("MobilityManager: client hasn't been asigned an agent: handing off client " + cntx.clientHwAddress
									+ " to agent " + stats.agentAddr + " at " + System.currentTimeMillis());
							handoffClientToAp(cntx.clientHwAddress, stats.agentAddr);
							updateStatsWithReassignment (stats, cntx.value, currentTimestamp, stats.agentAddr, stats.scanningResult);
							clientMap.put(cntx.clientHwAddress,stats);
							return;
						}
						
						// Check for out-of-range client
						// a client has sent nothing during a certain time
						if ((currentTimestamp - stats.lastHeard) > MOBILITY_PARAMS.idle_client_threshold) {
							log.info("MobilityManager: client with MAC address " + cntx.clientHwAddress
									+ " was idle longer than " + MOBILITY_PARAMS.idle_client_threshold/1000 + " sec -> Reassociating it to agent " + stats.agentAddr);
							handoffClientToAp(cntx.clientHwAddress, stats.agentAddr);
							updateStatsWithReassignment (stats, cntx.value, currentTimestamp, stats.agentAddr, stats.scanningResult);
							clientMap.put(cntx.clientHwAddress,stats);
							return;
						}
						
						if ((currentTimestamp - stats.lastHeard) > MOBILITY_PARAMS.time_reset_triggers) {
				            log.info("MobilityManager: Time threshold consumed");
				            updateStatsScans(stats,currentTimestamp, 0, 0);
				            clientMap.put(cntx.clientHwAddress,stats);
						}
						
						// If this notification is from the agent that's hosting the client's LVAP scan, update MobilityStats and handoff.
						if (client.getLvap().getAgent().getIpAddress().equals(cntx.agent.getIpAddress())) {
							
							
							/* Scan and update statistics */
							
							client_signal_dBm = stats.signalStrength - 256;
							client_average_dBm = stats.client_average - 256;
							client_triggers = stats.client_triggers;
							//log.info("MobilityManager: Triggers: "+ client_triggers);
							
							//log.info("MobilityManager: STA current power in this client: "+ stats.signalStrength + " (" + client_signal_dBm + "dBm)");
							
							
							if (client_triggers != MOBILITY_PARAMS.number_of_triggers){
								
								client_signal = Math.pow(10.0, (client_signal_dBm) / 10.0); // Linear power
								//log.info("client_signal: "+ client_signal);
								client_average = Math.pow(10.0, (client_average_dBm) / 10.0); // Linear power average
								//log.info("client_average: "+ client_average);
								client_average = client_average  + ((client_signal - client_average)/( client_triggers +1)); // Cumulative moving average
								//log.info("client_average: "+ client_average);
								client_average_dBm = Math.round(10.0*Math.log10(client_average)); //Average power in dBm
				                //log.info("client_average_dBm: "+ client_average_dBm);
				                client_triggers++; // Increase number of triggers that will be used to calculate average power
								
				                updateStatsScans(stats, currentTimestamp, client_average_dBm + 256, client_triggers);
				                clientMap.put(cntx.clientHwAddress,stats);
				                //log.info("MobilityManager: STA average power in this client: "+ stats.client_average + " (" + client_average_dBm + "dBm)");
				                //log.info("MobilityManager: Triggers: "+ stats.client_triggers);
				                
								return;
								
							}else {
								
								updateStatsScans(stats,currentTimestamp, 0, 0);
								clientMap.put(cntx.clientHwAddress,stats);
								
								// Don't bother if we're not within hysteresis period
				                if (currentTimestamp - stats.assignmentTimestamp < MOBILITY_PARAMS.hysteresis_threshold)
				                    return;
				                
				                log.info("MobilityManager: Scan triggered with average power: "+ (client_average_dBm + 256) + " (" + client_average_dBm + "dBm)");
							}
							
							
								
							for (InetAddress agentAddr: getAgents()) { // FIXME: scan for nearby agents only 
								
								// This is the agent where the STA is associated, so we don't scan
								if (cntx.agent.getIpAddress().equals(agentAddr)) {
									log.info("MobilityManager: Do not Scan client " + cntx.clientHwAddress + " in agent (Skip same AP) " + agentAddr + " and channel " + getChannelFromAgent(agentAddr));
									continue; // Skip same AP
								}
								// Scanning in the rest of APs
								else {
									log.info("MobilityManager: Scanning client " + cntx.clientHwAddress + " in agent " + agentAddr + " and channel " + getChannelFromAgent(cntx.agent.getIpAddress()));
									
									// Send the scanning request to the agent
									
				                    lastScanningResult = scanClientFromAgent(agentAddr, cntx.clientHwAddress, getChannelFromAgent(cntx.agent.getIpAddress()), this.MOBILITY_PARAMS.scanning_time);
				                    //log.info("MobilityManager: Last Scanning Result: "+lastScanningResult);
				                    
				                    //scan = false; // For testing only once
									//if (lastScanningResult >= 50) { // testing
										
									if (lastScanningResult > stats.signalStrength) {
										//greaterscanningresult = stats.signalStrength; 
										greaterscanningresult = lastScanningResult;// 
										updateStatsWithReassignment(stats, lastScanningResult, currentTimestamp, agentAddr, greaterscanningresult);
									}
									else if (greaterscanningresult < lastScanningResult) { // 
									      greaterscanningresult = lastScanningResult;
									     }
									
									
				                    }
									
				                //log.info("MobilityManager: Higher Scanning result: " + greaterscanningresult);
				                log.info("MobilityManager: Scanned client " + cntx.clientHwAddress + " in agent " + agentAddr + " and channel " + getChannelFromAgent(cntx.agent.getIpAddress()) + " with power " + lastScanningResult);
							}
							
							if (cntx.agent.getIpAddress().equals(stats.agentAddr)) {
								stats.scanningResult = greaterscanningresult;
								clientMap.put(cntx.clientHwAddress,stats);
								log.info("MobilityManager: no hand off");
								return;
							}
							
							log.info("MobilityManager: signal strengths: new = " + stats.signalStrength + " old = " + cntx.value + " handing off client " + cntx.clientHwAddress
										+ " to agent " + stats.agentAddr);
							handoffClientToAp(cntx.clientHwAddress, stats.agentAddr);
							clientMap.put(cntx.clientHwAddress,stats);
							//log.info("\n*\n*\n*\n*\n*\n*");
							return;
							
						}
						
					}
					
					/**
					 * This method will update statistics
					 *
					 * @param stats
					 * @param signalValue
					 * @param now
					 * @param agentAddr
					 * @param scanningResult
					 */
					private void updateStatsWithReassignment (MobilityStats stats, long signalValue, long now, InetAddress agentAddr, long scanningResult) {
						stats.signalStrength = signalValue;
						stats.lastHeard = now;
						stats.assignmentTimestamp = now;
						stats.agentAddr = agentAddr;
						stats.scanningResult = scanningResult;
						
					}
					
					
					/**
					 * This method will only update when trigger
					 *
					 * @param stats
					 * @param averagekey
					 * @param triggers
					 */
					private void updateStatsScans (MobilityStats stats, long now, long average, int triggers) {
				        stats.lastHeard = now;
						stats.client_average = average;
						stats.client_triggers = triggers;
					}
					
					
					/**
					 * Sleep
					 *
					 * @param time
					 */
					private void giveTime (int time) {
						try {
									Thread.sleep(time);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
					}
						
					/**
					 * It will be a method for channel assignment
					 *
					 * FIXME: Do it in a suitanObjectable way
					 */
					private void channelAssignment () {
						for (InetAddress agentAddr: getAgents()) {
							log.info("MobilityManager: Agent IP: " + agentAddr.getHostAddress());
							if (agentAddr.getHostAddress().equals("192.168.1.9")){
								log.info ("MobilityManager: Agent channel: " + getChannelFromAgent(agentAddr));
								setChannelToAgent(agentAddr, 1);
								log.info ("MobilityManager: Agent channel: " + getChannelFromAgent(agentAddr));
							}
							if (agentAddr.getHostAddress().equals("192.168.1.10")){
								log.info ("MobilityManager: Agent channel: " + getChannelFromAgent(agentAddr));
								setChannelToAgent(agentAddr, 11);
								log.info ("MobilityManager: Agent channel: " + getChannelFromAgent(agentAddr));
							}
							
						}
					}

					class MobilityStats {
						public long signalStrength;
						public long lastHeard;				// timestamp where it was heard the last time
						public long assignmentTimestamp;	// timestamp it was assigned
						public InetAddress agentAddr;
						public long scanningResult;
						public long client_average;				// average power
						public int client_triggers;				// number of triggers to calculate average power

						MobilityStats (long signalStrength, long lastHeard, long assignmentTimestamp, InetAddress agentAddr, long scanningResult, long average, int triggers) {
							this.signalStrength = signalStrength;
							this.lastHeard = lastHeard;
							this.assignmentTimestamp = assignmentTimestamp;
							this.agentAddr = agentAddr;
							this.scanningResult = scanningResult;
							this.client_average = average;
							this.client_triggers = triggers;
						}
					}
					
				}
				
				MobilityManagerCache mmc = new MobilityManagerCache();
				mmc.start();
			}
		};	
		
		new Thread(thread).start();		
	}
	
}