/*
 * WiCacheFileRequest: this is the class that
 * defines the file request that comes from the 
 * clients to the WiCache module
 */

package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiCacheFileRequest {
	
	protected static Logger log; //for logging 
	
	private String fileName; //file name
	
	private InetAddress clientAddr; //the client that requested this file
	
	private InetAddress agentAddr; //the agent/AP to which the client (that requests the file)  is connected
	
	public WiCacheFileRequest(String fileName, InetAddress clientAddr, InetAddress agentAddr) {
		// TODO Auto-generated constructor stub
		
		log = LoggerFactory.getLogger(CacheController.class);
		this.setFileName(fileName);
		this.setClientAddr(clientAddr);
		this.setAgentAddr(agentAddr);
	}
	
	/*
	 * setter methods
	 */
	
	public boolean setFileName(String fileName){
		
		if(fileName.equals(null)) {
			log.info("setFileName error: fileName cannot be null");
			return false;
		}
		
		this.fileName = fileName;
		return true;
	}
	
	public boolean setClientAddr(InetAddress clientAddr){
		
		if(clientAddr.equals(null)) {
			log.info("setClientAddr error: clientAddr cannot be null");
			return false;
		}
		
		this.clientAddr = clientAddr;
		return true;
	}
	
	public boolean setAgentAddr(InetAddress agentAddr){
		
		if(agentAddr.equals(null)) {
			log.info("setAgentAddr error: agentAddr cannot be null");
			return false;
		}
		
		this.agentAddr = agentAddr;
		return true;
	}
	
	/*
	 * getter methods
	 */
	
	public String getFileName(){
		return this.fileName;
	}
	
	public InetAddress getClientIp(){
		return this.clientAddr;
	}
	
	public InetAddress getAgentIp(){
		return this.agentAddr;
	}

}
