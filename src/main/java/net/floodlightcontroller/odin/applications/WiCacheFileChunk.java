/*
 * WiCacheFileChunk: this is the class that defines
 * the chunk/segment object that is cached in the 
 * cache of AP
 */

package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.sql.Time;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jmx.snmp.Timestamp;

import javafx.util.Pair;

public class WiCacheFileChunk {
	
	protected static Logger log; //logging
	
	private String fileName; //file name to which the chunk/segment belongs
	
	private long fileSize; //chunk/segment size
	
	private String chunkName; //chunk name
	
	private int chunkNo; //chunk number/id
	
	private long chunkSize; //size of the chunk
	
	private long chunkStartIndex; //starting byte number/index of the chunk
	
	private long chunkLastIndex; //ending byte number/index of the chunk
	
	private long chunkHitCount; //number of times the chunk is requested
	
	private ArrayList<InetAddress> agentList;	
	
	public WiCacheFileChunk(String chunkName, int chunkNo, long chunkSize, long chunkStartIndex, long chunkLastIndex, String fileName, long fileSize) {
		log = LoggerFactory.getLogger(CacheController.class);
		
		this.setFileName(fileName);	
		this.setFileSize(fileSize); //needed for setChunkSize
		
		this.setChunkName(chunkName);
		this.setChunkNo(chunkNo);			
		this.setChunkSize(chunkSize);
		this.setChunkStartIndex(chunkStartIndex);
		this.setChunkLastIndex(chunkLastIndex);
		this.setChunkHitCount(0);
		this.agentList = new ArrayList<InetAddress>();
		
	}
	
	/*
	 * setter methods 
	 */
	
	public boolean setChunkName(String chunkName){
		
		if(chunkName.equals(null)) {
			log.info("setChunkName error: chunkName is null");
			return false;
		}
		
		this.chunkName = chunkName;
		return true;
	}	
	
	public boolean setChunkNo(int chunkNo) {
		if(chunkNo<0) {
			log.info("setChunkNo error: chunkNo cannot be less than 0");
			return false;
		}
		
		this.chunkNo = chunkNo;
		return true;
	}
	
	public boolean setChunkSize(long chunkSize){
		
		if(chunkSize<=0) {
			log.info("setChunkSize error: chunkSize cannot be equal to or less than 0");
			return false;
		}
		
		this.chunkSize = chunkSize; 
		return true;
	}
	
	public boolean setChunkStartIndex(long chunkStartIndex) {
		if(chunkStartIndex<0 || chunkStartIndex > this.fileSize) {
			log.info("setChunkStartIndex error: chunkStartIndex cannot be less than 0 or greater than fileSize");
			return false;
		}
		this.chunkStartIndex = chunkStartIndex;
		return true;
	}
	
	public boolean setChunkLastIndex(long chunkLastIndex) {
		if(chunkLastIndex<0 || chunkLastIndex > this.fileSize) {
			log.info("setChunkLastIndex error: chunkLastIndex cannot be less than 0 or greater than fileSize");
			return false;
		}
		this.chunkLastIndex = chunkLastIndex;
		return true;
	}

	public boolean setChunkHitCount(long chunkHitCount){
		
		if(chunkHitCount<0) {
			log.info("setChunkHitCount error: chunkHitCount cannot be less than 0");
			return false;
		}
		
		this.chunkHitCount = chunkHitCount;
		return true;
	}	
	
	public long incChunkHitCount(){
		this.chunkHitCount++;
		return this.chunkHitCount;
	}
	
	public long decChunkHitCount(){
		
		if(this.chunkHitCount==0) {
			log.info("decChunkHitCount error: chunkHitCount cannot be less than 0");
			return this.chunkHitCount;
		}
		
		this.chunkHitCount--;
		return this.chunkHitCount;
	}
	
	public boolean addAgent(InetAddress agentAddr){
		
		if(agentAddr.equals(null)) {
			log.info("addAgent error: agentAddr cannot be null");
			return false;
		}
		//remove previous existing entry before adding new one
		else if(this.agentList.contains(agentAddr)) {			
			this.agentList.remove(agentAddr);
		}
		
		this.agentList.add(agentAddr);
		return true;
	}
	
	public boolean delAgent(InetAddress agentAddr){
		
		if(!this.agentList.contains(agentAddr)) {
			log.info("delAgent error: agentAddr does not exists");
			return false;
		}
		
		this.agentList.remove(agentAddr);
		return true;
	}
	
	public boolean setFileName(String fileName){
		
		if(fileName.equals(null)) {
			log.info("setFileName error: fileName is null");
			return false;
		}
		
		this.fileName = fileName;
		return true;
	}
	
	public boolean setFileSize(long fileSize){
		
		if(fileSize<=0) {
			log.info("setFileSize error: fileSize cannot be equal to or less than 0");
			return false;
		}
		
		this.fileSize = fileSize; 
		return true;
	}
	
	/*
	 * getter methods
	 */
	
	public String getChunkName(){
		return this.chunkName;
	}
	
	public int getChunkNo(){
		return this.chunkNo;
	}	
	
	public long getChunkSize(){
		return this.chunkSize;
	}
	
	public long getChunkStartIndex() {
		return this.chunkStartIndex;
	}
	
	public long getChunkLastIndex() {
		return this.chunkLastIndex;
	}
	
	public long getChunkHitCount(){
		return this.chunkHitCount;
	}	
	
	public ArrayList<InetAddress> getAgentList(){
		return this.agentList;
	}
	
	public String getFileName() {
		return this.fileName;
	}	
	
	public long getFileSize(){
		return this.fileSize;
	}
	
}