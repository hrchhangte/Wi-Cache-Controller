package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.odin.master.OdinApplication;

public class WiCacheAppContentDelivery extends WiCacheApplication {
	
	protected static Logger log = LoggerFactory.getLogger(CacheController.class);	
	@Override
	public void run() {
		// TODO Auto-generated method stub		
		try {			
			//do until there is a WiCacheFileRequest object
			while(true) {				
				WiCacheFileRequest fileReq =  getFileRequestObject();
				if(fileReq==null) {
					continue;					 
				}
				//if there is a WiCacheFileRequest object
				if(fileReq!=null) {					
					
					//get the list of agents and list of files
					ConcurrentHashMap<InetAddress, WiCacheAgent> agentList = getWiCacheAgentList();
					ConcurrentHashMap<String, WiCacheFile> fileList = getWiCacheFileList();
					//get the file object and the its chunks
					WiCacheFile fileObj = fileList.get(fileReq.getFileName());
					ArrayList<WiCacheFileChunk> chunkList = fileObj.getChunkList();				
					//for each of the chunk object, check their location (AP/agent)
					for(WiCacheFileChunk chunkObj: chunkList) {
						//if present in the current AP, send
						if(chunkObj.getAgentList().contains(fileReq.getAgentIp())) {
							sendSegmentToClient(chunkObj, 0, fileReq.getClientIp(), fileReq.getAgentIp());
						}
						else {//if not present in the current AP, select an agent which contains the chunk
							
							ArrayList<InetAddress> chunkAgentList = chunkObj.getAgentList();
							
							for(InetAddress agentIP: chunkAgentList) {
								
								WiCacheAgent chunkAgentObj = agentList.get(agentIP);
								
								if(chunkAgentObj.getChunkCachedList().contains(chunkObj.getChunkName())) {
									
									sendSegmentToClient(chunkObj, 0, fileReq.getClientIp(), chunkAgentObj.getAgentAddr());
									
								}
								
							}	
							
						}//else
						
					}// for WiCacheFileChunk			
					
				}//if !fileReq.equals
				
			}//while true 

			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
			
	}//run()		
	

}//WiCacheAppTest
