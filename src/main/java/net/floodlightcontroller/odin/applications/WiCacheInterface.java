/*
 * WiCacheInterface: this is the main interface that 
 * defines the function that would be implemented by the
 * class that implements the functionality of the WiCache module 
 * 
 */

package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public interface WiCacheInterface {
	
	/*
	 * getFileRequestObject: returns first entry of file request queue
	 */
	public WiCacheFileRequest getFileRequestObject();
	
	/*
	 * sendChunkToClient: sends the chunk/segment to the client 
	 */	
	public void sendSegmentToClient(WiCacheFileChunk chunkObj, long skipIndex, InetAddress clientAddr, InetAddress agentAddr);
	
	/*
	 * addChunkToCache: add/place the chunk/segment to the cache/AP
	 */	
	public void addSegmentToCache(WiCacheFileChunk chunkObj, InetAddress agentAddr, InetAddress srcAddr, int srcPort);
	
	/*
	 * delChunkFromCache: delete/remove the chunk/segment from the cache/AP
	 */
	public void delSegmentFromCache(WiCacheFileChunk chunkObj, InetAddress agentAddr);
	
	/*
	 * getAgentChunkList: returns fileCachedList of an agent
	 */
	
	public ConcurrentHashMap<String, WiCacheChunkEntry> getAgentChunkList(InetAddress agentAddr);
	
	/*
	 * getAgentDownloadList: returns downloadList of an agent 
	 */
	public ConcurrentHashMap<InetAddress, ChunkDownloadEntry> getAgentDownloadList(InetAddress agentAddr);
	
	/*
	 *getWiCacheFileObject:  returns WiCacheFile's object for a file
	 */
	public WiCacheFile getWiCacheFileObject(String fileName);
	
	/*
	 * getWiCacheFileChunkObject: returns WiCacheFileChunk's object for a chunk
	 */
	public WiCacheFileChunk getWiCacheFileChunkObject(String chunkName);
	
	/*
	 * getWiCacheAgent: returns WiCacheAgent's object for an agent address
	 */	
	public WiCacheAgent getWiCacheAgent(InetAddress agentAddr);
	
	/*
	 *getWiCacheAgentList:  returns list of WiCacheAgent objects
	 */
	public ConcurrentHashMap<InetAddress, WiCacheAgent> getWiCacheAgentList();
	
	/*
	 * getWiCacheFileList: returns list of WiCacheFile objects
	 */
	public ConcurrentHashMap<String, WiCacheFile> getWiCacheFileList();
	
}
