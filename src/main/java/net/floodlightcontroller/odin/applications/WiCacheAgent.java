/*
 * WiCacheAgent: this is the class the defines
 * the WiCache agent's context at the WiCache module 
 */

package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiCacheAgent {
	
	protected static Logger log;  //for logging
	
	private InetAddress agentAddr; //the IP address of the agent/AP
	
	private long totalCacheSize;  //the total cache size of the agent/AP
	
	private long freeCacheSize; //available cache size for caching
	
	private long chunksCachedCount; //number of chunks/segments cached at the agent/AP
	
	private long downloadsCount; //number of downloads currently going on
	
	private ConcurrentHashMap<String, WiCacheChunkEntry> chunkCachedList; //map of chunk/segment id and the entry object
	
	private ConcurrentHashMap<InetAddress, ChunkDownloadEntry> downloadList; //map of client's IP address and the download entry object
	
	public WiCacheAgent(InetAddress agentAddr, long totalCacheSize){
		// TODO Auto-generated constructor stub
		log = LoggerFactory.getLogger(WiCacheMobility.class);
		this.setAgentAddr(agentAddr);
		this.setTotalCacheSize(totalCacheSize);
		this.setFreeCacheSize(totalCacheSize);
		this.setChunksCachedCount(0);
		this.setDownloadsCount(0);
		this.downloadList = new ConcurrentHashMap<InetAddress, ChunkDownloadEntry>();
		this.chunkCachedList = new ConcurrentHashMap<String, WiCacheChunkEntry>();
	}
	
	/*
	 * setter methods
	 */
	
	public InetAddress setAgentAddr(InetAddress agentAddr) {
		if(agentAddr.equals(null)) {
			log.info("setAgentAddr error: null");
		}
		this.agentAddr = agentAddr;
		return this.agentAddr;
	}
	
	public boolean setTotalCacheSize(long totalCacheSize){
		
		if(totalCacheSize<0) {
			log.info("setTotalCacheSize error: totalCacheSize cannot be less than 0" );
			return false;
		}
		
		this.totalCacheSize = totalCacheSize;
		return true;
	}
	
	public boolean setFreeCacheSize(long freeCacheSize){
		
		if(freeCacheSize<0 || freeCacheSize>this.totalCacheSize) {
			log.info("setFreeCacheSize error: freeCacheSize cannot be less than or more totalCacheSize");
			return false;
		}
			
		this.freeCacheSize = freeCacheSize;
		return true;
	}
	
	public boolean incFreeCacheSize(long incValue){
		
		if(incValue<0) {
			log.info("incFreeCacheSize error: incValue cannot be less than 0");
			return false;
		}
		
		if(this.freeCacheSize+incValue>this.totalCacheSize) {
			log.info("incFreeCacheSize error: total freeCacheSize cannot be more than totalCacheSize");
			return false;
		}
		
		this.freeCacheSize = this.freeCacheSize + incValue;
		return true;
	}
	
	public boolean decFreeCacheSize(long decValue){
		
		if(decValue<0) {
			log.info("decFreeCacheSize error: decValue cannot be less than 0");
			return false;
		}
		
		if(this.freeCacheSize-decValue<0) {
			log.info("freeCacheSize error: cannot be less than 0");
			return false;
		}
		
		this.freeCacheSize = this.freeCacheSize - decValue;
		return true;
	}
	
	public boolean setChunksCachedCount(long chunksCachedCount){
		
		if(chunksCachedCount<0) {
			log.info("setChunksCachedCount error: chunksCachedCount cannot be less than 0");
			return false;
		}
		
		this.chunksCachedCount = chunksCachedCount;
		return true;
	}
	
	public long incChunksCachedCount(){

		this.chunksCachedCount++;
		return this.chunksCachedCount;
	}
	
	public long decChunksCachedCount(){
		
		if(this.chunksCachedCount==0) {
			log.info("decChunksCachedCount error: chunksCachedCount cannot be less than 0");
			return this.chunksCachedCount;
		}
		
		this.chunksCachedCount--;
		return this.chunksCachedCount;
	}
	
	public boolean setDownloadsCount(long downloadsCount){
		
		if(downloadsCount<0) {
			log.info("setDownloadsCount error: downloadsCount cannot be less than 0");
			return false;
		}
		
		this.downloadsCount = downloadsCount;
		return true;
	}
	
	public long incDownloadsCount(){
		this.downloadsCount++;
		return this.downloadsCount;
	}	
	
	public long decDownloadsCount(){
		
		if(this.downloadsCount==0) {
			log.info("decDownloadsCount error: downloadsCount cannot be less than 0");
			return this.downloadsCount;
		}
		
		this.downloadsCount--;
		return this.downloadsCount;
	}
	
	public boolean addChunkEntry(String chunkName, WiCacheChunkEntry chunkEntry){
		
		if(this.freeCacheSize<chunkEntry.getChunkSize()) {
			log.info("addChunkEntry error: not enough free space in cache");
			return false;
		}
		
		this.chunkCachedList.put(chunkName, chunkEntry);
		this.chunksCachedCount++;
		this.freeCacheSize = this.freeCacheSize - chunkEntry.getChunkSize();
		return true;
	}
	
	public boolean delChunkEntry(String chunkName) {
		
		if(!this.chunkCachedList.containsKey(chunkName)) {
			log.info("delChunkEntry error: chunkName does not exists");
			return false;
		}
		
		this.chunksCachedCount--;
		this.freeCacheSize = this.freeCacheSize + this.chunkCachedList.get(chunkName).getChunkSize();
		this.chunkCachedList.remove(chunkName);	
		return true;
	}
	
	public boolean addDownload(InetAddress clientAddr, ChunkDownloadEntry downloadEntry){
		
		if(!this.chunkCachedList.containsKey(downloadEntry.getChunkName())) {
			log.info("addDownload error: chunkName does not exists in the cache");
			return false;
		}
		
		this.downloadList.put(clientAddr, downloadEntry);
		this.downloadsCount++;
		return true;
	}
	
	public boolean delDownload(InetAddress clientAddr) {
		
		if(!this.downloadList.containsKey(clientAddr)) {
			log.info("delDownload error: downloadEntry does not exists");
			return false;
		}
		
		this.downloadsCount--;
		this.downloadList.remove(clientAddr);	
		return true;
	}
	
	/*
	 * getter methods
	 */
	
	public InetAddress getAgentAddr() {
		return this.agentAddr;
	}
	
	public long getTotalCacheSize(){
		return this.totalCacheSize;
	}
	
	public long getFreeCacheSize(){
		return this.freeCacheSize;
	}
	
	public long getFilesCachedCount(){
		return this.chunksCachedCount;
	}
	
	public long getDownloadsCount(){
		return this.downloadsCount;
	}
	
	public WiCacheChunkEntry getChunkCachedEntry(String chunkName){
		
		return this.chunkCachedList.get(chunkName);
	}
	
	public ConcurrentHashMap<String, WiCacheChunkEntry> getChunkCachedList(){
		return this.chunkCachedList;
	}
	
	public ChunkDownloadEntry getDownloadEntry(InetAddress clientAddr){
		return this.downloadList.get(clientAddr);
	}
	
	public ConcurrentHashMap<InetAddress, ChunkDownloadEntry> getDownloadList(){
		return this.downloadList;
	}
	
	/*
	 * getMinHitCount: returns list of file entries with minimum hit count
	 */
	
	public List<WiCacheChunkEntry> getMinHitCount(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		long minHitCount = iterCount.next().getValue().getHitCount();
		List<WiCacheChunkEntry> minList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the minimum hit count value
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getHitCount() < minHitCount){
				minHitCount = curChunkCachedEntry.getHitCount();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();
		//collect all the entry with minimum hit count value
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getHitCount() == minHitCount){
				minList.add(curChunkCachedEntry);
			}
		}
			
		return minList;
	}
	
	/*
	 * getMaxHitCount: returns list of file entries with maximum file hit count
	 */

	public List<WiCacheChunkEntry> getMaxHitCount(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		long maxHitCount = iterCount.next().getValue().getHitCount();
		List<WiCacheChunkEntry> maxList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the maximum hit count value
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getHitCount() > maxHitCount){
				maxHitCount = curChunkCachedEntry.getHitCount();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();
		//collect all the entry with maximum hit count value
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getHitCount() == maxHitCount){
				maxList.add(curChunkCachedEntry);
			}
		}
			
		return maxList;
	}

	/*
	 * getMinCachedTime: returns list of file entries with minimum time of cached
	 */
	
	public List<WiCacheChunkEntry> getMinCachedTime(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		Timestamp minCachedTime = iterCount.next().getValue().getTimeOfCached();
		List<WiCacheChunkEntry> minList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the minimum cached time
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getTimeOfCached().getTime() < minCachedTime.getTime()){
				minCachedTime = curChunkCachedEntry.getTimeOfCached();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();
		//collect all the entry with minimum cached time
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getTimeOfCached() == minCachedTime){
				minList.add(curChunkCachedEntry);
			}
		}
			
		return minList;
	}
	
	/*
	 * getMaxCachedTime: returns list of file entries with maximum time of cached
	 */
	
	public List<WiCacheChunkEntry> getMaxCachedTime(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		Timestamp maxCachedTime = iterCount.next().getValue().getTimeOfCached();
		List<WiCacheChunkEntry> maxList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the maximum cached time
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getTimeOfCached().getTime() > maxCachedTime.getTime()){
				maxCachedTime = curChunkCachedEntry.getTimeOfCached();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();
		//collect all the entry with maximum cached time
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getTimeOfCached() == maxCachedTime){
				maxList.add(curChunkCachedEntry);
			}
		}
			
		return maxList;
	}

	/*
	 * getMinLastAccessedTime: returns list of file entries with minimum last accessed time
	 */
	
	public List<WiCacheChunkEntry> getMinLastAccessedTime(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		Timestamp minLastAccessTime = iterCount.next().getValue().getLastAccessedTime();
		List<WiCacheChunkEntry> minList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the minimum last accessed time
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getLastAccessedTime().getTime() < minLastAccessTime.getTime()){
				minLastAccessTime = curChunkCachedEntry.getLastAccessedTime();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();		
		//collect all the entry with minimum cached time
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getLastAccessedTime() == minLastAccessTime){
				minList.add(curChunkCachedEntry);
			}
		}
			
		return minList;
	}
	
	/*
	 * getMaxLastAccessedTime: returns list of file entries with maximum last accessed time
	 */


	public List<WiCacheChunkEntry> getMaxLastAccessedTime(){
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iterCount = chunkCachedList.entrySet().iterator();
		Timestamp maxLastAccessTime = iterCount.next().getValue().getLastAccessedTime();
		List<WiCacheChunkEntry> maxList = new ArrayList<WiCacheChunkEntry>(); 
		
		//find the maximum last accessed time
		while(iterCount.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iterCount.next().getValue();
			if(curChunkCachedEntry.getLastAccessedTime().getTime() > maxLastAccessTime.getTime()){
				maxLastAccessTime = curChunkCachedEntry.getLastAccessedTime();
			}
		}
		
		Iterator<Map.Entry<String, WiCacheChunkEntry>> iter = chunkCachedList.entrySet().iterator();
		//collect all the entry with maximum cached time
		while(iter.hasNext()){
			
			WiCacheChunkEntry curChunkCachedEntry = iter.next().getValue();
			if(curChunkCachedEntry.getLastAccessedTime() == maxLastAccessTime){
				maxList.add(curChunkCachedEntry);
			}
		}
			
		return maxList;
	}
	
}

/*
 * WiCacheChunkEntry: this is the class that defines
 * the entry that represents the chunk/segment that
 * is cached in the agent/AP 
 */

class WiCacheChunkEntry{
	
	protected static Logger log; 
	private String chunkName;
	private String fileName;
	private long chunkSize;
	private long hitCount;
	private Timestamp timeOfCached;
	private Timestamp lastAccessedTime;
	
	public WiCacheChunkEntry(String chunkName, String fileName, long chunkSize, Timestamp timeOfCached) {
		// TODO Auto-generated constructor stub
		
		log = LoggerFactory.getLogger(WiCacheMobility.class);
		this.setChunkName(chunkName);
		this.setFileName(fileName);
		this.setChunkSize(chunkSize);
		this.setHitCount(0); 
		this.setTimeOfCached(timeOfCached);
		this.setLastAccessedTime(timeOfCached);
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
	
	public boolean setFileName(String fileName){
		
		if(fileName.equals(null)) {
			log.info("setFileName error: fileName is null");
			return false;
		}
		
		this.fileName = fileName;
		return true;
	}
	
	public boolean setChunkSize(long chunkSize){
		
		if(chunkSize<0) {
			log.info("setChunkSize error: chunkSize cannot be less than 0");
			return false;
		}
				
		this.chunkSize = chunkSize;
		return true;
	}
	
	public boolean setHitCount(long hitCount){
		
		if(hitCount<0) {
			log.info("setHitCount error: hitCount cannot be less than 0");
			return false;
		}
		
		this.hitCount = hitCount;
		return true;
	}
	
	public long incHitCount(){
		this.hitCount++;
		return this.hitCount;
	}
	
	public long decHitCount(){
		
		if(this.hitCount==0) {
			log.info("decHitCount error: cannot be less than 0");
			return this.hitCount;
		}
		
		this.hitCount--;
		return this.hitCount;
	}
	
	public Timestamp setTimeOfCached(Timestamp timeOfCached){
		this.timeOfCached = timeOfCached;
		return this.timeOfCached;
	}
	
	public Timestamp setLastAccessedTime(Timestamp lastAccessedTime){
		this.lastAccessedTime = lastAccessedTime;
		return this.lastAccessedTime;
	}
	
	/*
	 * getter methods
	 */
	
	public String getChunkName(){
		return this.chunkName;
	}
	
	public String getFileName() {
		return this.fileName;
	}
	
	public Long getChunkSize(){
		return this.chunkSize;
	}
	
	public Long getHitCount(){
		return this.hitCount;
	}
	
	public Timestamp getTimeOfCached(){
		return this.timeOfCached;
	}
	
	public Timestamp getLastAccessedTime(){
		return this.lastAccessedTime;
	}
}
/*
 * ChunkDownloadEntry: this is the class that defines an
 * entry that represents the chunk/segment that is currently
 * being downloaded
 */
class ChunkDownloadEntry{
	
	protected static Logger log; 
	private InetAddress clientAddr;
	private String chunkName;
	private String fileName;
	private long chunkSize;
	private long bytesDownloaded;	
	
	public ChunkDownloadEntry(InetAddress clientAddr, String chunkName, String fileName, long chunkSize) {
		this.setClientAddr(clientAddr);
		this.setChunkName(chunkName);
		this.setFileName(fileName);
		this.setChunkSize(chunkSize);
		this.setBytesDownloaded(0);
	}
	
	/*
	 * setter methods 
	 */
	
	public boolean setClientAddr(InetAddress clientAddr){
		
		if(clientAddr.equals(null)) {
			log.info("setClientAddr error: clientAddr is null");
			return false;
		}
		
		this.clientAddr = clientAddr;
		return true;
	}
	
	public boolean setChunkName(String chunkName){
		
		if(chunkName.equals(null)) {
			log.info("setChunkName error: chunkName is null");
			return false;
		}
		
		this.chunkName = chunkName;
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
	
	public boolean setChunkSize(long chunkSize) {
		
		if(chunkSize<0) {
			log.info("setChunkSize error: chunkSize cannot be less than 0");
			return false;
		}
		
		this.chunkSize = chunkSize;
		return true;
	}
	
	public boolean setBytesDownloaded(long bytesDownloaded){
		
		if(this.bytesDownloaded > this.chunkSize || this.bytesDownloaded < 0) {
			log.info("setBytesDownloaded error: bytesDownloaded cannot be more than fileSize or less than 0");
			return false;
		}
		
		this.bytesDownloaded = bytesDownloaded;
		return true;
	}
	
	public boolean incBytesDownloaded(long incValue){
		
		if(incValue<0) {
			log.info("incBytesDownloaded error: incValue cannot be less than 0");
			return false;
		}
		
		if(this.bytesDownloaded+incValue>this.chunkSize) {
			log.info("incBytesDownloaded error: Total bytesDownloaded cannot be more than fileSize");
			return false;
		}
		
		this.bytesDownloaded = this.bytesDownloaded + incValue;
		return true;
	}
	
	public boolean decBytesDownloaded(long decValue){
		
		if(decValue<0) {
			log.info("decBytesDownloaded error: decValue cannot be less than 0");
			return false;
		}
		
		if(this.bytesDownloaded-decValue<0) {
			log.info("decBytesDownloaded error: bytesDownloaded cannot be less than 0");
			return false;
		}
		
		this.bytesDownloaded = this.bytesDownloaded - decValue;
		return true;
	}
	
	/*
	 * getter methods
	 */
	
	public InetAddress getClientAddr(){
		return this.clientAddr;
	}
	
	public String getChunkName(){
		return this.chunkName;
	}
	
	public String getFileName() {
		return this.fileName;
	}
	
	public long getChunkSize() {
		return this.chunkSize;
	}
	
	public Long getBytesDownloaded(){
		return this.bytesDownloaded;
	}
}

