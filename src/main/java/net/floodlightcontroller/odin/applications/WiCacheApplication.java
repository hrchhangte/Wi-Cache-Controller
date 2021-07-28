/*
 * WiCacheApplication: this is the abstract class that should be
 * implemented by the WiCache module, and these are invoked/
 * called by WiCache applications 
 * 
 */

package net.floodlightcontroller.odin.applications;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.odin.master.OdinApplication;

public abstract class WiCacheApplication extends OdinApplication implements Runnable{
	
	private WiCacheInterface wiCacheInterfaceToApplication;
	
	public abstract void run();
	
	final void setWiCacheInterface (WiCacheInterface wm) {
		this.wiCacheInterfaceToApplication = wm;
	}
	
	protected final WiCacheFileRequest getFileRequestObject() {
		return this.wiCacheInterfaceToApplication.getFileRequestObject();
	}
	
	protected final void sendSegmentToClient(WiCacheFileChunk chunkObj, long skipIndex, InetAddress clientAddr, InetAddress agentAddr) {
		this.wiCacheInterfaceToApplication.sendSegmentToClient(chunkObj, skipIndex, clientAddr, agentAddr);
	}
	
	protected final void addSegmentToCache(WiCacheFileChunk chunkObj, InetAddress agentAddr, InetAddress srcAddr, int srcPort) {
		this.wiCacheInterfaceToApplication.addSegmentToCache(chunkObj, agentAddr, srcAddr, srcPort);
	}
	
	protected final void delSegmentFromCache(WiCacheFileChunk chunkObj, InetAddress agentAddr) {
		this.wiCacheInterfaceToApplication.delSegmentFromCache(chunkObj, agentAddr);
	}
	
	protected final ConcurrentHashMap<String, WiCacheChunkEntry> getAgentChunkList(InetAddress agentAddr){
		return this.wiCacheInterfaceToApplication.getAgentChunkList(agentAddr);
	}
	
	protected final ConcurrentHashMap<InetAddress, ChunkDownloadEntry> getAgentDownloadList(InetAddress agentAddr){
		return this.wiCacheInterfaceToApplication.getAgentDownloadList(agentAddr);
	}
	
	protected final WiCacheFile getWiCacheFileObject(String fileName) {
		return this.wiCacheInterfaceToApplication.getWiCacheFileObject(fileName);
	}
	
	protected final WiCacheFileChunk getWiCacheFileChunkObject(String chunkName) {
		return this.wiCacheInterfaceToApplication.getWiCacheFileChunkObject(chunkName);
	}
	
	protected final WiCacheAgent getWiCacheAgent(InetAddress agentAddr) {
		return this.wiCacheInterfaceToApplication.getWiCacheAgent(agentAddr);
	}
	
	protected final ConcurrentHashMap<InetAddress, WiCacheAgent> getWiCacheAgentList(){
		return this.wiCacheInterfaceToApplication.getWiCacheAgentList();
	}
	
	protected final ConcurrentHashMap<String, WiCacheFile> getWiCacheFileList(){
		return this.wiCacheInterfaceToApplication.getWiCacheFileList();
	}
	
}
