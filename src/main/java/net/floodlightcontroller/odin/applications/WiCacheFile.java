/*
 * WiCacheFile: this is the class that defines
 * the file object that is cached in the 
 * cache of AP
 */


package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiCacheFile {
	
	protected static Logger log; //for logging
	
	private String fileName; //the file name
	
	private long fileSize; //the file size
	
	private long fileHitCount; // the number of times the file is requested
	
	private ArrayList<WiCacheFileChunk> chunkList; //the list of chunks/segments that belong to the file
	
	private double filePopularity; //the file popularity metric
	
	private boolean fileSplit; //whether the file is split into chunks/segments
	
	public WiCacheFile(String fileName, long fileSize, double filePopularity, boolean fileSplit) {
		log = LoggerFactory.getLogger(WiCacheMobility.class);
		this.setFileName(fileName);
		this.setFileSize(fileSize);
		this.setFileHitCount(0);	
		this.chunkList = new ArrayList<WiCacheFileChunk>();
		this.setFilePopularity(filePopularity);
		this.setFileSplit(fileSplit);
	}
	
	/*
	 * setter methods 
	 */
	
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

	public boolean setFileHitCount(long fileHitCount){
		
		if(fileHitCount<0) {
			log.info("setFileHitCount error: fileHitCount cannot be less than 0");
			return false;
		}
		
		this.fileHitCount = fileHitCount;
		return true;
	}	
	
	public long incFileHitCount(){
		this.fileHitCount++;
		return this.fileHitCount;
	}
	
	public long decFileHitCount(){
		
		if(this.fileHitCount==0) {
			log.info("decFileHitCount error: fileHitCount cannot be less than 0");
			return this.fileHitCount;
		}
		
		this.fileHitCount--;
		return this.fileHitCount;
	}
	
	public boolean addChunk(int chunkNo, WiCacheFileChunk fileChunk){
		
		if(fileChunk.equals(null)) {
			log.info("addChunk error: fileChunk cannot be null");
			return false;
		}	
		
		//existing one will be replaced
		this.chunkList.add(chunkNo, fileChunk);
		return true;
	}
	
	public boolean delChunk(WiCacheFileChunk fileChunk){
		
		if(!this.chunkList.contains(fileChunk)) {
			log.info("delChunk error: fileChunk does not exists");
			return false;
		}
		
		this.chunkList.remove(fileChunk);
		return true;
	}
	
	public boolean delChunk(int chunkNo){
		
		if(chunkNo<0 || chunkNo>=this.chunkList.size()) {
			log.info("delChunk error: chunkNo does not exists");
			return false;
		}
		
		this.chunkList.remove(chunkNo);
		return true;
		
	}
	
	public boolean setFilePopularity(double filePopularity) {
		
		if(filePopularity<0 || filePopularity>1) {
			log.info("setFilePopularity error: file popularity should be in the range [0,1]");
			return false;
		}
		
		this.filePopularity = filePopularity;
		return true;
	}
	
	public boolean setFileSplit(boolean fileSplit) {
		this.fileSplit = fileSplit;
		return true;
	}
	
	/*
	 * getter methods
	 */
	
	public String getFileName(){
		return this.fileName;
	}
	
	public long getFileSize(){
		return this.fileSize;
	}
	
	public long getFileHitCount(){
		return this.fileHitCount;
	}	
	
	public ArrayList<WiCacheFileChunk> getChunkList(){
		return this.chunkList;
	}	
	
	public WiCacheFileChunk getFileChunk(int chunkNo) {
		return this.chunkList.get(chunkNo);		
	}
	
	public WiCacheFileChunk getFileChunk(String chunkName) {
		
		int i=0;
		while(i<this.chunkList.size()) {
			if(this.chunkList.get(i).getChunkName().equals(chunkName)) {
				return this.chunkList.get(i);
			}			
			i++;
		}
		
		return null;
	}
	
	public double getFilePopularity() {
		return this.filePopularity;
	}
	
	public boolean getFileSplit() {
		return this.fileSplit;
	}

}
