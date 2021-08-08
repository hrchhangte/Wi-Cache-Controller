/*
 * WiCacheMain: This is the main class that implements the 
 * functionalities of the WiCache module that is part of the
 * WiCache controller 
 * 
 * It implements the APIs that are defined in WiCacheInterface
 * that can be called by WiCache applications
 * 
 */
package net.floodlightcontroller.odin.applications;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.text.ParseException;

import org.python.google.common.net.InetAddresses;
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

import com.backblaze.erasure.*;

/*
 * WiCacheMain extends the OdinApplication and runs a thread on top of the Odin framework
 * The methods that are to be implemented by WiCacheMain are defined in WiCacheInterface
 */
public class WiCacheMain extends OdinApplication implements WiCacheInterface {
	
	protected static Logger log;  //for logging
	
	private String ctrIP; //the WiCache controller IP
	
	private ServerSocket clntLstnSock, agntLstnSock; //the socket that listens for incoming connection from clients and agents
	
	private Socket clntSockMain, agntSockMain; //the socket of clients and agents after establishing a connection
	
	private int clntMesgPort, agntMesgPort; //port nos. for control mesg. of clients and agents
	
	private LinkedList<WiCacheFileRequest> fileRequestQueue; //list of file/segment request coming from the clients
	
	private ConcurrentHashMap<InetAddress, Socket> agntSockMap;  //map of client IP address and the socket
		
	private ConcurrentHashMap<InetAddress, WiCacheAgent> wicacheAgentList; //map of agent IP address and agent object
	
	private ConcurrentHashMap<String, WiCacheFile> wicacheFileList;	//map of file/segment name/id and the corres. object
	
	private ConcurrentHashMap<InetAddress, LinkedList<ClientChunkEntry>> wicacheDownloadList; //map of client IP address and the list of file/segment/chunk being downloaded
	 	
	private String fileStoragePath; //main storage path
	
	private String nfsSharesPath; //path to the nfs share, linked to the WiCache APs
	
	private String initFile; //initialization/configuration file
	
	private String initFileList; //list of files for initialization of caches
	
	private String appName; //the WiCache application to be executed
	
	private InetAddress fileServerAddr; //remote/file server address
	
	private int fileServerPort; //remote/file server port
	
	private String splitFiles; //whether files are to be split or not
	
	private long minChunkSize; //minimum chunk/segment size
	
	private int maxChunkCount; //maximum chunk/segment count per file
	
	private String fileCoding; //whether file coding is to be used
	
	private String filesStatusOutput; //output file that contains status of files
	
	private String agentsStatusOutput; //output file that contains status of agents	
	
	private String filesStatusOutputRun; //output file that contains the running status of files
	
	private String agentsStatusOutputRun; //output file that contains the running status of agents
	
	private DatagramSocket udpSock; //udp sock, used for status update
	
	private int udpPort; //udp port corres. to udpSock	
	
	private String cacheInitFile; //the file that contains the cache initialization for the APs	
	
	private String cacheReplacePolicy; //the policy that is used for cache replacement
	
	/*
	 * following are used only if coding is enabled
	 * coding used is Solomon coding
	 */
	public static final int DATA_SHARDS = 8;
	
    public static final int PARITY_SHARDS = 4;
    
    public static final int TOTAL_SHARDS = 12;
    
    public static final int BYTES_IN_INT = 4;
   
    @Override
	public void run() {
		// TODO Auto-generated method stub
		//will be overridden by the application
		
    	//initialize the data structures
		wiCacheMainInit();
		
		//read the initial configuration file
		readInitFile();
		
		//doReedSolomonCoding();
		
		//reedSolomonDecoder("file3.mp4");
		
		//create chunks/segments 
		makeChunks();
		
		//cache the chunks/segments
		cacheChunksUniform();		
		
		//read the file/segment status
		readFileStatus();
		
		//read the agent status
		readAgentStatus();
		
		//print for debugging purpose
		printToTerminal();
		
		//write to file for debugging purpose
		printToFile();
		
		//perform actual writing to the caches
		initWriteToCaches();
		
		//handle incoming connection request from the agents
		handleAgent();	
		
		//handle incoming connection request from the clients
		handleClient();
		
		//start the WiCache applications
		startApp();		
	}
    
    /*
     * reedSolomonDecoder: method that performs 
     * the decoding of the encoded files
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void reedSolomonDecoder(String fileName){
    	
    	try {			
    	
            final File originalFile = new File(this.fileStoragePath+fileName);
            if (!originalFile.exists()) {
                System.out.println("Cannot read input file: " + originalFile);
                return;
            }

            // Read in any of the shards that are present.
            // (There should be checking here to make sure the input
            // shards are the same size, but there isn't.)
            final byte [] [] shards = new byte [TOTAL_SHARDS] [];
            final boolean [] shardPresent = new boolean [TOTAL_SHARDS];
            int shardSize = 0;
            int shardCount = 0;
            for (int i = 0; i < TOTAL_SHARDS; i++) {
                File shardFile = new File(
                        originalFile.getParentFile(),
                        originalFile.getName() + "_" + i);
                if (shardFile.exists()) {
                	log.info(""+shardFile.getAbsolutePath());
                	log.info("fileName:" +shardFile.getName());
                    shardSize = (int) shardFile.length();
                    shards[i] = new byte [shardSize];
                    shardPresent[i] = true;
                    shardCount += 1;
                    InputStream in = new FileInputStream(shardFile);
                    in.read(shards[i], 0, shardSize);
                    in.close();
                    System.out.println("Read " + shardFile);
                }
            }

            // We need at least DATA_SHARDS to be able to reconstruct the file.
            if (shardCount < DATA_SHARDS) {
                System.out.println("Not enough shards present");
                return;
            }

            // Make empty buffers for the missing shards.
            for (int i = 0; i < TOTAL_SHARDS; i++) {
                if (!shardPresent[i]) {
                    shards[i] = new byte [shardSize];
                }
            }

            // Use Reed-Solomon to fill in the missing shards
            ReedSolomon reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS);
            reedSolomon.decodeMissing(shards, shardPresent, 0, shardSize);

            // Combine the data shards into one buffer for convenience.
            // (This is not efficient, but it is convenient.)
            byte [] allBytes = new byte [shardSize * DATA_SHARDS];
            for (int i = 0; i < DATA_SHARDS; i++) {
                System.arraycopy(shards[i], 0, allBytes, shardSize * i, shardSize);
            }

            // Extract the file length
            int fileSize = ByteBuffer.wrap(allBytes).getInt();

            // Write the decoded file
            File decodedFile = new File(originalFile.getParentFile(), originalFile.getName() + ".decoded");
            OutputStream out = new FileOutputStream(decodedFile);
            out.write(allBytes, BYTES_IN_INT, fileSize);
            System.out.println("Wrote " + decodedFile);
    		
		} catch (Exception e) {
			// TODO: handle exception
		}
    	
    }
    /*
     * reedSolomonEncoder: method that performs the encoding
     * of the files 
     */
    public void reedSolomonEncoder(String fileName, long fileSizeLong, double filePopularity, boolean fileSplit){

    	try {   		
    		
    		/*
            // Parse the command line
            if (arguments.length != 1) {
                System.out.println("Usage: SampleEncoder <fileName>");
                return;
            }
            final File inputFile = new File(arguments[0]);
            if (!inputFile.exists()) {
                System.out.println("Cannot read input file: " + inputFile);
                return;
            }
            */
        	
        	final File inputFile = new File(this.fileStoragePath+fileName);  
        	
        	WiCacheFile fileObj = new WiCacheFile(fileName, fileSizeLong, filePopularity, fileSplit);

            // Get the size of the input file.  (Files bigger that
            // Integer.MAX_VALUE will fail here!)
            //final int fileSize = (int) inputFile.length();
        	final int fileSize = (int) fileSizeLong;
        	
            // Figure out how big each shard will be.  The total size stored
            // will be the file size (8 bytes) plus the file.
            final int storedSize = fileSize + BYTES_IN_INT;
            final int shardSize = (storedSize + DATA_SHARDS - 1) / DATA_SHARDS;

            // Create a buffer holding the file size, followed by
            // the contents of the file.
            final int bufferSize = shardSize * DATA_SHARDS;
            final byte [] allBytes = new byte[bufferSize];
            ByteBuffer.wrap(allBytes).putInt(fileSize);
            InputStream in = new FileInputStream(inputFile);
            int bytesRead = in.read(allBytes, BYTES_IN_INT, fileSize);
            if (bytesRead != fileSize) {
                throw new IOException("not enough bytes read");
            }
            in.close();

            // Make the buffers to hold the shards.
            byte [] [] shards = new byte [TOTAL_SHARDS] [shardSize];

            // Fill in the data shards
            for (int i = 0; i < DATA_SHARDS; i++) {
                System.arraycopy(allBytes, i * shardSize, shards[i], 0, shardSize);
            }

            // Use Reed-Solomon to calculate the parity.
            ReedSolomon reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS);
            reedSolomon.encodeParity(shards, 0, shardSize);

            // Write out the resulting files.
            for (int i = 0; i < TOTAL_SHARDS; i++) {
                File outputFile = new File(
                        inputFile.getParentFile(),
                        inputFile.getName() + "_" + i);
                
                String chunkName = inputFile.getName() + "_" + i;                
                
                int chunkNo = i;
                long chunkSizeAssigned = shardSize;
                long chunkStartIndex = 0;
                long chunkLastIndex = 0;
                
              //create chunk object and add to parent file object
                WiCacheFileChunk fileChunk = new WiCacheFileChunk(chunkName, chunkNo, chunkSizeAssigned, chunkStartIndex, chunkLastIndex, fileName, fileSize);		
				fileObj.addChunk(chunkNo, fileChunk);
                
                OutputStream out = new FileOutputStream(this.fileStoragePath+chunkName);
                out.write(shards[i]);
                out.close();
                System.out.println("wrote " + outputFile);
            }
            
          //add file object to file list
            wicacheFileList.put(fileName, fileObj);
			
		} catch (Exception e) {
			// TODO: handle exception
		}
    	
    }		
	
	public void wiCacheMainInit() {
		
		try {
			
			log = LoggerFactory.getLogger(WiCacheMobility.class);
						
			//this.ctrIP = "192.168.1.145";
			
			this.clntSockMain = null;
			this.clntMesgPort = 9000; //can be set here or read from a file
			this.clntLstnSock = new ServerSocket(this.clntMesgPort);
			
			this.agntSockMain = null;
			this.agntMesgPort = 8000; //can be set here or read from a file
			this.agntLstnSock = new ServerSocket(this.agntMesgPort);		
			this.agntSockMap = new ConcurrentHashMap<InetAddress, Socket>();										
			
			this.fileStoragePath = "/home/userdir/FileStorage/";
			this.nfsSharesPath = "/home/userdir/agentsNFSShares/";
			this.initFile = "/home/userdir/shareddir/odin-wi5-controller/initFile";
			this.initFileList = "/home/userdir/shareddir/odin-wi5-controller/fileList";
			
			this.filesStatusOutput = "/home/userdir/shareddir/odin-wi5-controller/status/filesStatusOutput.csv";
			this.agentsStatusOutput = "/home/userdir/shareddir/odin-wi5-controller/status/agentsStatusOutput.csv";
			this.filesStatusOutputRun = "/home/userdir/shareddir/odin-wi5-controller/status/filesStatusOutputRun.csv";
			this.agentsStatusOutputRun = "/home/userdir/shareddir/odin-wi5-controller/status/agentsStatusOutputRun.csv";
			
			this.appName = null;
			this.fileServerAddr = null;
			this.fileServerPort = 0;
			this.splitFiles = null;
			this.minChunkSize = 0;
			this.maxChunkCount = 0;
			this.fileCoding = null;
			this.cacheInitFile = null;
			this.cacheReplacePolicy = null;
						
			this.udpPort = 7000; //can be set here or read from a file
			this.udpSock = new DatagramSocket(udpPort);			
			
			//Data structures
			this.fileRequestQueue = new LinkedList<WiCacheFileRequest>();		
			this.wicacheAgentList = new ConcurrentHashMap<InetAddress, WiCacheAgent>();
			this.wicacheFileList = new ConcurrentHashMap<String, WiCacheFile>();		
			this.wicacheDownloadList = new ConcurrentHashMap<InetAddress, LinkedList<ClientChunkEntry>>();
			
			log.info("wiCacheMainInit: variables initialized!");
			
		} catch (Exception e) {
			// TODO: handle exception
		}	
		
	}
	
	/*
	 * readInitFile: reads the initialization/configuration file
	 */
	public void readInitFile() {
		
		try {					
			
				String line = null;
				FileInputStream fis = new FileInputStream(this.initFile);
				BufferedReader br = new BufferedReader(new InputStreamReader(fis));		
									
				String[] words = null;
				
				while ((line = br.readLine()) != null) {
					words = line.split("\\s+");											
					
					if(words[0].equals("agent-configuration")) {
			    						    		
			    		while(!(line = br.readLine()).equals("#")) {
			    			words = line.split("\\s+");
			    			
			    			InetAddress agentAddr = InetAddress.getByName(words[0]);
				    		long freeCacheSize = Long.parseLong(words[1]);
				    		log.info("readInitFile: agentAddr: "+agentAddr+" freeCacheSize: "+freeCacheSize);
				    		
				    		//create WiCacheAgent object for an agent and add to wicacheAgentList
				    		wicacheAgentList.put(agentAddr, new WiCacheAgent(agentAddr, freeCacheSize));					    		
			    			
			    		}		    		
			    		
			    	}											
					
					else if(words[0].equals("split-files")) {
						this.splitFiles = words[1];
						log.info("readInitFile: split-files: "+this.splitFiles);
					}
					
					else if(words[0].equals("min-segment-size")) {
						this.minChunkSize = Long.parseLong(words[1]);
						log.info("readInitFile: min-chunk-size: "+this.minChunkSize);
					}
					
					else if(words[0].equals("max-segment-count")) {
						this.maxChunkCount = Integer.parseInt(words[1]);
						log.info("readInitFile: max-chunk-count: "+this.maxChunkCount);
					}
					
					else if(words[0].equals("file-coding")) {
						this.fileCoding = words[1];							
						log.info("readInitFile: file-coding: "+this.fileCoding);
					}
					
					//currently this is not used, instead
					//after chunks/segments are created from file
					//they are automatically distributed across the APs					
					else if(words[0].equals("cache-init-file")) {
						this.cacheInitFile = words[1];
						log.info("readInitFile: cache-init-file: "+this.cacheInitFile);
					}
					
					else if(words[0].equals("cache-replacement")) {
						this.cacheReplacePolicy = words[1];
						log.info("readInitFile: cache-replacement: "+this.cacheReplacePolicy);
					}
					
					else if(words[0].equals("applications")) {							
						
						line = br.readLine();
						words = line.split("\\s+");
						
						this.appName = words[0];						
						log.info("readInitFile: appName: "+this.appName);														
					}		    	
			    	 
			    }//while
			    
			    br.close();
			    fis.close();
			    
			    log.info("InitCaches: configuration file initFile read successfully");
			    
			} catch (Exception e) {
				// TODO: handle exception
			}			
	}
	
	
	/*
	 * doReedSolomonCoding: performs reed solomon coding
	 * when enabled 
	 */
	public void doReedSolomonCoding() {		
		
		try {			
			
			FileInputStream fis = new FileInputStream(this.initFileList);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			
			String line = null;
			String words[] = null;
			while((line=br.readLine())!=null) {
								
				words = line.split("\\s+");
				String fileName = words[0];
				long fileSize = Long.parseLong(words[1]);
				double filePopularity = Double.parseDouble(words[2]);
				String split = words[3];
				boolean fileSplit = false;
				
				//individual file attribute: Y for split, N for no-split
				if(split.equals("Y")) {
					fileSplit = true;
				}
				
				else if(split.equals("N")) {
					fileSplit = false;
				}
				
				reedSolomonEncoder(fileName, fileSize, filePopularity, fileSplit);
			
		}//line			
		
		br.close();
	
		log.info("makeChunk: Chunks created successfully");
		
		} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
		}		
		
	}
	
	/*
	 * makeChunks: create chunks from the files specified/provided
	 */
	public void makeChunks() {
		
		try {			
			
			FileInputStream fis = new FileInputStream(this.initFileList);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			
			String line = null;
			String words[] = null;
			while((line=br.readLine())!=null) {
								
				words = line.split("\\s+");
				
				//ignore comment lines
				if(words[0].equals("#")) {
					continue;
				}
				
				String fileName = words[0];
				long fileSize = Long.parseLong(words[1]);
				double filePopularity = Double.parseDouble(words[2]);
				String split = words[3];
				boolean fileSplit = false;
				
				//individual file attribute: Y for split, N for no-split
				if(split.equals("Y")) {
					fileSplit = true;
				}
				
				else if(split.equals("N")) {
					fileSplit = false;
				}				
				
				log.info("makeChunks: fileName: "+fileName+" fileSize: "+fileSize+" filePopularity: "+filePopularity+" fileSplit: "+fileSplit);
				
				WiCacheFile fileObj = new WiCacheFile(fileName, fileSize, filePopularity, fileSplit);
				
				//check the global split files setting as well
				if(fileSplit==true && this.splitFiles.equals("yes")) {
					
					//determine chunk size guide based on constraints
					int chunkCountDiv = this.maxChunkCount;
					long chunkSize = Math.floorDiv(fileSize, chunkCountDiv);
					while(chunkSize<this.minChunkSize) {
						chunkCountDiv--;
						chunkSize = Math.floorDiv(fileSize, chunkCountDiv);
					}				    					
			    	
			    	 //split based on chunk constraints
			    	 long remainingBytes = fileSize;
			    	 long chunkStartIndex = 0; //start index will start from 1
			    	 long chunkLastIndex = 0;
			    	 int chunkNo = -1; //chunk numbering will start from 0
			    	 long chunkSizeAssigned = 0;
			    	 
			    	 while(remainingBytes > 0) {
						
			    		chunkNo = chunkNo + 1;
			    		chunkSizeAssigned = 0;
			    		chunkStartIndex = chunkLastIndex + 1;
						
						if((remainingBytes-chunkSize)>=chunkSize) {
							
							chunkLastIndex = chunkStartIndex + chunkSize - 1;
							chunkSizeAssigned = chunkSize;					
							
						}
						else {
							
							chunkLastIndex = fileSize;
							chunkSizeAssigned = chunkSize + (remainingBytes - chunkSize);
							
						}						
						
						remainingBytes = remainingBytes - chunkSizeAssigned;
						
						String chunkName = fileName+"_"+chunkNo;
						
						log.info("chunkName: "+chunkName+" chunkNo: "+chunkNo+" chunkSizeA: "+chunkSizeAssigned+" chunkStartI: "+chunkStartIndex+" chunkLastI: "+chunkLastIndex+" fileSize: "+fileSize);
						//create chunk object and add to parent file object
						WiCacheFileChunk fileChunk = new WiCacheFileChunk(chunkName, chunkNo, chunkSizeAssigned, chunkStartIndex, chunkLastIndex, fileName, fileSize);		
						fileObj.addChunk(chunkNo, fileChunk);
						
					}//remainingBytes
					
				}//if fileSplit == true				
				
				else if(fileSplit==false) {
					
					String chunkName = fileName+"_0";
					int chunkNo = 0;
					long chunkSizeAssigned = fileSize;
					long chunkStartIndex = 1;
					long chunkLastIndex = fileSize;
					
					WiCacheFileChunk fileChunk = new WiCacheFileChunk(chunkName, chunkNo, chunkSizeAssigned, chunkStartIndex, chunkLastIndex, fileName, fileSize);		
					fileObj.addChunk(chunkNo, fileChunk);
					
				}//if fileSplit == false
		    	 
		    	 //add file object to file list
		    	 wicacheFileList.put(fileName, fileObj);		    	
				
			}//line			
			
			br.close();
		
			log.info("makeChunk: Chunks created successfully");
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
	}
	
	/*
	 * cacheChunksUniform: distribute the chunks/segments
	 * of a file uniformly across the caches
	 */
	public void cacheChunksUniform() {	
		
		Iterator<Map.Entry<InetAddress, WiCacheAgent>> agentIter = wicacheAgentList.entrySet().iterator();
		
		while(agentIter.hasNext()){
			
			WiCacheAgent agentObj = agentIter.next().getValue();
			long freeCacheSize= agentObj.getFreeCacheSize();
			long uniformCacheSize = Math.floorDiv(freeCacheSize, wicacheFileList.size());
			log.info("uniform size: "+uniformCacheSize);
			
			Iterator<Map.Entry<String, WiCacheFile>> fileIter = wicacheFileList.entrySet().iterator();
			
			while(fileIter.hasNext()) {
				
				WiCacheFile fileObj = fileIter.next().getValue();
				int noOfChunks = fileObj.getChunkList().size();
				
				ArrayList <Integer> chunkIndices = new ArrayList<Integer>();
				int i = 0;
				while(i<noOfChunks) {
					chunkIndices.add(i);
					i++;
				}
				
				long remainingSpace = uniformCacheSize;
				while(!chunkIndices.isEmpty()) {
					
					Collections.shuffle(chunkIndices);
					int chunkIndex = chunkIndices.get(0);
					WiCacheFileChunk chunkObj = fileObj.getChunkList().get(chunkIndex);
					
					if(chunkObj.getChunkSize()<=remainingSpace) {
						
						//update agent object, agent list
						Timestamp timeOfCached = new Timestamp(System.currentTimeMillis());
						WiCacheChunkEntry chunkEntryObj = new WiCacheChunkEntry(chunkObj.getChunkName(), chunkObj.getFileName(), chunkObj.getChunkSize(), timeOfCached);
						wicacheAgentList.get(agentObj.getAgentAddr()).addChunkEntry(chunkObj.getChunkName(), chunkEntryObj);
						
						//update chunk object, file object, file list
						wicacheFileList.get(chunkObj.getFileName()).getChunkList().get(chunkObj.getChunkNo()).addAgent(agentObj.getAgentAddr());	
						
						log.info("assignment: chunk: "+chunkEntryObj.getChunkName()+" agent: "+agentObj.getAgentAddr());
						
						remainingSpace = remainingSpace - chunkObj.getChunkSize();					
						chunkIndices.remove(0);
					}
					else {
						
						chunkIndices.remove(0);
						
					}					
				}//while(chunkNos)									
					
			}//fileIter
			
		} //agentIter
		
		log.info("cacheChunksUniform: chunks distributed successfully");
				
	}	
	
	/*
	 * readFileStatus: read the file status of the last run
	 */
	public void readFileStatus() {
		
		try {
			
			//delete wicacheFileList and wicacheAgentList
			this.wicacheFileList = null;
			this.wicacheFileList = new ConcurrentHashMap<String, WiCacheFile>();
			
			FileInputStream fis = new FileInputStream(filesStatusOutput);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			
			String line = null;
			String[] tokens = null;
			
			line=br.readLine();
			tokens = line.split(",");
			//no of agents = no of columns - 10
			int noOfAgents = tokens.length - 10; 
			int i = 0;
			ArrayList<InetAddress> agentList = new ArrayList<InetAddress>();
			while(i<noOfAgents) {
				
				InetAddress agentAddr = InetAddress.getByName(tokens[10+i]);
				agentList.add(i,agentAddr);
				
				i++;
			}//while i
			
			while((line=br.readLine())!=null) {
				
				log.info(line);
				tokens = line.split(",");
				String chunkName = tokens[0];
				int usIndex = chunkName.indexOf('_');
				int chunkNo = Integer.parseInt(chunkName.substring(usIndex+1,chunkName.length()));				
				long chunkSize = Long.parseLong(tokens[1]);				
				long chunkStartIndex = Long.parseLong(tokens[2]);
				long chunkLastIndex = Long.parseLong(tokens[3]);
				long chunkHitCount = Long.parseLong(tokens[4]);
				String fileName = tokens[5];
				long fileSize = Long.parseLong(tokens[6]);
				long fileHitCount = Long.parseLong(tokens[7]);
				double filePopularity = Double.parseDouble(tokens[8]);
				String split = tokens[9];
				boolean fileSplit = false;
				
				if(split.equals("true")) {
					//log.info("yes sir");
					fileSplit = true;
				}
				
				else if(split.equals("false")) {
					fileSplit = false;
				}
				
				//if file list does not contain the file object yet, create the file object
				if(!wicacheFileList.containsKey(fileName)) {
					WiCacheFile fileObj = new WiCacheFile(fileName, fileSize, filePopularity, fileSplit);
					fileObj.setFileHitCount(fileHitCount);
					wicacheFileList.put(fileName, fileObj);
				}
				
				//create an object for every chunk, update fields and add to file object, file list
				WiCacheFileChunk chunkObj = new WiCacheFileChunk(chunkName, chunkNo, chunkSize, chunkStartIndex, chunkLastIndex, fileName, fileSize);
				chunkObj.setChunkHitCount(chunkHitCount);
				
				i = 0;
				while(i<noOfAgents) {
					
					String cachedStatus = tokens[10+i];
					if(cachedStatus.equals("Y")) {
						chunkObj.addAgent(agentList.get(i)); log.info("here is the mark");
						
						//update agent object status also
						/*
						 * set local hit count = 0
						 * set time of cached and last accessed time = current time
 						 */
						Timestamp timeOfCached = new Timestamp(System.currentTimeMillis());		
						WiCacheChunkEntry entryObj = new WiCacheChunkEntry(chunkName, fileName, chunkSize, timeOfCached);
						entryObj.setLastAccessedTime(timeOfCached);//update
						int hitCount = 0;
						entryObj.setHitCount(hitCount);//update
						
						log.info("here");
						wicacheAgentList.get(agentList.get(i)).addChunkEntry(chunkName, entryObj);
						
					}
					
					i++;
				}//while i				
				
				wicacheFileList.get(fileName).addChunk(chunkNo, chunkObj);
				
			}//while line
			
			br.close();
			
		} catch (Exception e) {
			// TODO: handle exception
		}	
		
	}
	
	/*
	 * readAgentStatus: read the status of the agents of the last run
	 */
	public void readAgentStatus(){
		
		try {
			
			//delete wicacheFileList and wicacheAgentList
			this.wicacheAgentList = null;
			this.wicacheAgentList = new ConcurrentHashMap<InetAddress, WiCacheAgent>();
			
			FileInputStream fis = new FileInputStream(agentsStatusOutput);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
			
			String line = null;
			String[] tokens = null;
			
			while((line = br.readLine())!=null) {
				log.info(line);
				tokens = line.split(",");
				
				if(tokens[0].equals("Agent IP")) {
					line = br.readLine(); log.info(line);
					tokens = line.split(",");
					
					InetAddress agentAddr = InetAddress.getByName(tokens[0]);
					long totalCacheSize = Long.parseLong(tokens[1]);
					//long freeCacheSize = Long.parseLong(tokens[2]); //auto update
					//long totalCachedCount = Long.parseLong(tokens[3]); //auto update
					//long totalDownloadsCount = Long.parseLong(tokens[4]); //not preserved
					
					WiCacheAgent agentObj = new WiCacheAgent(agentAddr, totalCacheSize);
					wicacheAgentList.put(agentAddr, agentObj);
					
					//skip two lines
					line = br.readLine(); //blank space
					line = br.readLine();//header
					
					while(!(line=br.readLine()).equals(",,,,")) {
						tokens = line.split(",");
						
						log.info("inside 1"); log.info(line);
						
						String chunkName = tokens[0];
						int usIndex = chunkName.indexOf('_');
						String fileName = chunkName.substring(0, usIndex);
						long chunkSize = Long.parseLong(tokens[1]);
						long chunkHitCount = Long.parseLong(tokens[2]);						
						
						try {
							SimpleDateFormat  dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
							Date parsedDate = dateFormat.parse(tokens[3]);
							java.sql.Timestamp timeOfCached = new Timestamp(parsedDate.getTime());
							//Timestamp timeOfCached = null;
							
							parsedDate = (Date) dateFormat.parse(tokens[4]);
							java.sql.Timestamp lastAccessedTime = new Timestamp(parsedDate.getTime());
							//Timestamp lastAccessedTime = null;
							
							WiCacheChunkEntry entryObj = new WiCacheChunkEntry(chunkName, fileName, chunkSize, timeOfCached);
							entryObj.setLastAccessedTime(lastAccessedTime);//update
							entryObj.setHitCount(chunkHitCount);//update
							
							log.info("here last");
							wicacheAgentList.get(agentAddr).addChunkEntry(chunkName, entryObj);
							
						} catch (Exception e) {
							// TODO: handle exception
						}						
						
					}//while line
					
				}//if tokens	
				
			}//while line
			
			br.close();
			
		} catch (Exception e) {
			// TODO: handle exception
		}	
		
	}
	
	/*
	 * initWriteToCaches: perform the actual writing of the
	 * chunks/segments to the caches
	 */
	public void initWriteToCaches() {
		
		try {
		
				Iterator<Map.Entry<String, WiCacheFile>> fileIter = wicacheFileList.entrySet().iterator();
				
				while(fileIter.hasNext()) {				
					
					WiCacheFile fileObj = fileIter.next().getValue();
					ArrayList< WiCacheFileChunk> chunkList = fileObj.getChunkList();					
					
					for(WiCacheFileChunk chunkObj: chunkList) {			
						
						for(InetAddress agentAddr: chunkObj.getAgentList()) {
						
								FileInputStream fis = new FileInputStream(new File(fileStoragePath+fileObj.getFileName()));
								DataInputStream dis = new DataInputStream(fis);
							
								FileOutputStream fos = new FileOutputStream(nfsSharesPath+agentAddr.getHostAddress()+"/"+chunkObj.getChunkName());
								
								log.info("initWriteToCaches: Writing: "+chunkObj.getChunkName()+" to "+agentAddr.getHostAddress());
					    		 
					    		 //add/copy/send the file														 
					    		 dis.skip(chunkObj.getChunkStartIndex()-1); //skip (start index - 1)
					    		 long remaining = chunkObj.getChunkSize();					
					    		 byte[] buffer = new byte[4096];
					    		 int read =0;
									
					    		 while(remaining > 0) {
					    			 //log.info("read and write");
									read = dis.read(buffer,0,buffer.length);
									fos.write(buffer,0,read);					
									remaining = remaining - read;
								}
					    		 
					    		 fos.close();
					    		 dis.close();
					    		 
						}//for InetAddress					
						
						
					}//for WiCacheFileChunk			
				
				}//while(fileIter)
				
				log.info("initWriteToCaches: Chunks written to caches successfully");
				
			}catch(Exception e){
				e.printStackTrace();
			}
		
	}	
	
	/*
	 * printToTerminal: print the files/chunks, agents
	 * status information
	 * mainly for debugging purpose
	 */
	
	public void printToTerminal() {
		
		//all files info
		log.info("-----all files info-----");
		Iterator<Map.Entry<String, WiCacheFile>> fileListIter = wicacheFileList.entrySet().iterator();		
		
		while(fileListIter.hasNext()) {
			
			//file info
			log.info("\n-----file info-----");
			WiCacheFile fileObj = fileListIter.next().getValue();
			log.info("File Name: "+fileObj.getFileName());
			log.info("File Size :"+fileObj.getFileSize());
			log.info("File Hit Count: "+fileObj.getFileHitCount());
			log.info("File Popularity: "+fileObj.getFilePopularity());
			log.info("File Split: "+fileObj.getFileSplit());
			
			log.info("\n-----all file chunks info-----");
			ArrayList<WiCacheFileChunk> chunksList = fileObj.getChunkList();
			
			for(WiCacheFileChunk chunkObj: chunksList) {
				
				log.info("\n-----chunk info-----");
				log.info("Chunk Name: "+chunkObj.getChunkName());
				log.info("Chunk Size: "+chunkObj.getChunkSize());
				log.info("Chunk Start Index: "+chunkObj.getChunkStartIndex());
				log.info("Chunk Last Index: "+chunkObj.getChunkLastIndex());
				log.info("Chunk Hit Count: "+chunkObj.getChunkHitCount());
				
				log.info("Chunk Agents: ");
				ArrayList<InetAddress> chunkAgentList = chunkObj.getAgentList();
				for(InetAddress agentAddr: chunkAgentList) {
					
					log.info(agentAddr.getHostAddress());
					
				}
				
			}//for WiCacheFileChunk
			
		}//while fileListIter		
	
		//all agents info
		log.info("-----all agent's info-----");
		Iterator<Map.Entry<InetAddress, WiCacheAgent>> agentIter = wicacheAgentList.entrySet().iterator();
		
		while(agentIter.hasNext()){
			
			//agent's info
			log.info("\n-----agent's info-----");
			WiCacheAgent agentObj = agentIter.next().getValue();
						
			log.info("Agent IP Address: "+agentObj.getAgentAddr());
			log.info("Total Cache Size: "+agentObj.getTotalCacheSize());
			log.info("Free Cache Size: "+agentObj.getFreeCacheSize());
			log.info("Chunks Cached Count: "+agentObj.getFilesCachedCount());			
			log.info("Downloads Count: "+agentObj.getDownloadsCount());
			
			//info of files cached in an agent
			log.info("\n-----info of chunks cached in an agent-----");
			ConcurrentHashMap<String, WiCacheChunkEntry> fileCachedList = agentObj.getChunkCachedList();
			Iterator<Map.Entry<String, WiCacheChunkEntry>> filesCachedIter = fileCachedList.entrySet().iterator();
						
			while(filesCachedIter.hasNext()) {
			
				WiCacheChunkEntry fileChunkObj = filesCachedIter.next().getValue();
				
				log.info("\n-----agent's chunk info-----");
				log.info("Chunk Name: "+fileChunkObj.getChunkName());
				log.info("Chunk Size: "+ fileChunkObj.getChunkSize());
				log.info("Hit Count: "+fileChunkObj.getHitCount());
				log.info("Last Accessed Time: "+fileChunkObj.getLastAccessedTime());
				log.info("Time Of Cached: "+fileChunkObj.getTimeOfCached());
				
			}		
			
		}	
		
	}	
	
	/*
	 * printToFile: write the status of the files/chunks, agents
	 * to a file to save the status of the run, can be read later
	 */
	public void printToFile() {			
		
		try {
			
			FileWriter writer = new FileWriter(this.filesStatusOutputRun);
			BufferedWriter bw = new BufferedWriter(writer);
			PrintWriter pw = new PrintWriter(bw);
			
			Iterator<Map.Entry<InetAddress, WiCacheAgent>> agentIter = wicacheAgentList.entrySet().iterator();
			
			String agentIPListTab = "";
			while(agentIter.hasNext()){
				
				WiCacheAgent agentObj = agentIter.next().getValue();
				agentIPListTab = agentIPListTab + agentObj.getAgentAddr().getHostAddress() + "\t";
					
			}
			
			//all files' info
			Iterator<Map.Entry<String, WiCacheFile>> fileListIter = wicacheFileList.entrySet().iterator();
				
			pw.println("Chunk Name\tChunk Size\tChunk Start Index\tChunk Last Index\tChunk Hit Count\tFile Name\tFile Size\tFile Hit Count\tFile Popularity\tFile Split\t"+agentIPListTab);
			
			while(fileListIter.hasNext()) {
				
				//file's info
				WiCacheFile fileObj = fileListIter.next().getValue();
				
				ArrayList<WiCacheFileChunk> chunkList = fileObj.getChunkList();
				
				for(WiCacheFileChunk chunkObj: chunkList) {						
					
					agentIter = wicacheAgentList.entrySet().iterator();
					String fileAgentMap = "";
					
					//agents where the file is cached
					ArrayList<InetAddress> chunkAgentList = chunkObj.getAgentList();
					
					while(agentIter.hasNext()){
						
						WiCacheAgent agentObj = agentIter.next().getValue();
						if(chunkAgentList.contains(agentObj.getAgentAddr())) {						
							fileAgentMap = fileAgentMap + "Y" + "\t";						
						}			
						else {
							fileAgentMap = fileAgentMap + "N" + "\t";
						}
							
					}//while agentIter
					
					pw.println(chunkObj.getChunkName()+"\t"+chunkObj.getChunkSize()+"\t"+chunkObj.getChunkStartIndex()+"\t"+chunkObj.getChunkLastIndex()
					+"\t"+chunkObj.getChunkHitCount()+"\t"+chunkObj.getFileName()+"\t"+fileObj.getFileSize()+"\t"+fileObj.getFileHitCount()+"\t"+fileObj.getFilePopularity()+"\t"+
							fileObj.getFileSplit()+"\t"+fileAgentMap);
					
				}//for WiCacheFileChunk			
				
			}//while fileListIter		
			
			pw.close();
			
			writer = new FileWriter(this.agentsStatusOutputRun);
			bw = new BufferedWriter(writer);
			pw = new PrintWriter(bw);
			
			//all agents info
			agentIter = wicacheAgentList.entrySet().iterator();					
			
			while(agentIter.hasNext()){			
				
				pw.println("Agent IP\tTotal Cache Size\tFree Cache Size\tChunks Cached Count\tChunks Download Count");
				WiCacheAgent agentObj = agentIter.next().getValue();
				pw.println(agentObj.getAgentAddr().getHostAddress()+"\t"+agentObj.getTotalCacheSize()+"\t"+agentObj.getFreeCacheSize()+"\t"+agentObj.getFilesCachedCount()
				+"\t"+agentObj.getDownloadsCount());							
				
				ConcurrentHashMap<String, WiCacheChunkEntry> fileCachedList = agentObj.getChunkCachedList();
				Iterator<Map.Entry<String, WiCacheChunkEntry>> filesCachedIter = fileCachedList.entrySet().iterator();
						
				pw.println("");
				pw.println("Chunk Name\tChunk Size\tChunk Hit Count (Local)\tTime of Cached\tLast Accessed Time");
				
				while(filesCachedIter.hasNext()) {
				
					WiCacheChunkEntry fileChunkObj = filesCachedIter.next().getValue();
					
					pw.println(fileChunkObj.getChunkName()+"\t"+fileChunkObj.getChunkSize()+"\t"+fileChunkObj.getHitCount()+"\t"+fileChunkObj.getTimeOfCached()+"\t"
					+fileChunkObj.getLastAccessedTime());
					
				}	
				
				pw.println("");
				
			}			
			
			pw.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}	
	
	/*
	 * handleAgent: handles incoming connection from agents
	 * Add socket entry to agntSockMap
	 * 
	 */
	
	public void handleAgent(){
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					log.info("handleAgent: Agent listener thread started");
														
					while(true){
						log.info("handleAgent: Waiting for connection from agents");
						agntSockMain = agntLstnSock.accept();						
												
						log.info("handleAgent: New connection accepted from agent : "+agntSockMain.getInetAddress());
						
						//create an entry in agent-socket mapping hash-table, replaces any previous entry
						agntSockMap.put(agntSockMain.getInetAddress(), agntSockMain);
						
						//retrieve the socket handle for the agent
						Socket agntSock = agntSockMap.get(agntSockMain.getRemoteSocketAddress());
						PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());		

						//set the size of cache of the agent
						agntWrtr.write("SET_CACHE" +" "+wicacheAgentList.get(agntSockMain.getRemoteSocketAddress()).getTotalCacheSize()+" ");
						log.info("SET_CACHE" +" "+wicacheAgentList.get(agntSockMain.getRemoteSocketAddress()).getTotalCacheSize()+" ");
						
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};	
		new Thread(thread).start();			
	}
	
	/*
	 * startApp: start the execution of the WiCache applications
	 */
	public void startApp() {
		
		try {
			Thread.sleep(1000);
			
			WiCacheApplication appInstance = (WiCacheApplication) Class.forName(this.appName).newInstance();
			appInstance.setWiCacheInterface(this);				    		
			appInstance.run();	
			
		} catch (Exception e) {
			e.printStackTrace();
		}			
	}
	
	/*
	 * handleClient: handles incoming connection from clients
	 */
	
	//main thread
	public void handleClient(){		
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {		
			
						//print current client-agent mapping status after every connection
						while(true){				
							
							log.info("handleClient: Current Client-Agent Mapping Status:");
							
							//get client list and agent list
							HashSet<OdinClient> clientList = new HashSet<OdinClient>(getClients());				
							
							Iterator<OdinClient> iter = clientList.iterator();
							OdinClient clntObj = null;
							while(iter.hasNext()){				
								clntObj = iter.next();
								if(!clntObj.getIpAddress().getHostAddress().equals("0.0.0.0") && clntObj.getIpAddress()!=null){
									
									log.info("handleClient: Client IP: "+clntObj.getIpAddress()+" LVAP: "+clntObj.getLvap().getBssid()+" Agent IP: "+clntObj.getLvap().getAgent().getIpAddress());
								}
							}		
										
							log.info("handleClient: Waiting for connection from clients");
							
							clntSockMain = clntLstnSock.accept();			
							InetAddress clientAddr = clntSockMain.getInetAddress();
							
							BufferedReader clntMesgRdr = new BufferedReader(new InputStreamReader(clntSockMain.getInputStream()));
							String clntMesg = clntMesgRdr.readLine();
							
							log.info("handleClient: Mesg. received from client "+clientAddr+": "+clntMesg);
							//split the message to tokens
							//message format: <FILE_REQ> <fileName> <code:0-9>
							String[] tokens = clntMesg.split("\\s+");
							String fileName = tokens[1];				
							
							//send the no. of chunks back to the client
							//message format: <SEG_COUNT>	 <segCount>
							int chunkCount = wicacheFileList.get(fileName).getChunkList().size();
							PrintWriter agntWrtr = new PrintWriter(clntSockMain.getOutputStream());			
							agntWrtr.println("CHUNK_COUNT"+" "+chunkCount+" ");
							agntWrtr.flush();
							
							//get the client object from client list
							clientList = new HashSet<OdinClient>(getClients());
							iter = clientList.iterator();
							clntObj = null;
							while(iter.hasNext()){
								clntObj = iter.next();
								if(clntObj.getIpAddress().equals(clientAddr)){
									
									log.info("handleClient: Client object found!");				
									
									//InetAddress desiredAgent = InetAddress.getByName("192.168.1.12");
									InetAddress desiredAgent = clntObj.getLvap().getAgent().getIpAddress();
									log.info("Assinging client to agent: "+desiredAgent);
									handoffClientToAp(clntObj.getMacAddress(), desiredAgent);
									handoffClientToAp(clntObj.getMacAddress(), desiredAgent);
									
									
									break;
								}
							} 
							
							//get the client's agent's address
							InetAddress agentAddr = clntObj.getLvap().getAgent().getIpAddress();
							
							//create file request object and add to the queue
							WiCacheFileRequest fr = new WiCacheFileRequest(fileName, clientAddr, agentAddr);
							log.info("handleClient: "+fr.getFileName()+" "+fr.getClientIp()+" "+fr.getAgentIp());
							fileRequestQueue.add(fr); //adds at the end
							log.info("Size: "+fileRequestQueue.size());
							
							//update/ total hit count
							wicacheFileList.get(fileName).incFileHitCount();
							
							//start a thread for the client
							//remove existing entry for the client
							if(wicacheDownloadList.containsKey(clientAddr)) {
								wicacheDownloadList.remove(clientAddr);
							}
							
						
							//log.info("file name: "+getFileRequestObject().getFileName());
							
							//update the download list for the client first
							sequential();
							//then instruct the agent to send the segments
							sendToClientThread(fileName, clientAddr);
							
						}//while(true)
			
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	
				
			}
			
		};
		
		new Thread(thread).start();
		
	}//handleClient()
	
	/*
	 * sequential: perform sequential delivery of chunk/segments
	 * a file to clients
	 */
	public void sequential() {
		
		Runnable thread = new Runnable() {			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				try {
					
					log.info("inside sequential");
				
					while(true) {
						
						//log.info("inside while");
						WiCacheFileRequest fileReq =  getFileRequestObject();
						if(fileReq==null) {
							continue;
							//log.info("null"); 
						}
						
						if(fileReq!=null) {
							
							log.info("Message exists!!");				
							
							//algorithm: sequential
							ConcurrentHashMap<InetAddress, WiCacheAgent> agentList = getWiCacheAgentList();
							ConcurrentHashMap<String, WiCacheFile> fileList = getWiCacheFileList();
							
							WiCacheFile fileObj = fileList.get(fileReq.getFileName());
							ArrayList<WiCacheFileChunk> chunkList = fileObj.getChunkList();				
							
							for(WiCacheFileChunk chunkObj: chunkList) {								
								
								if(chunkObj.getAgentList().contains(fileReq.getAgentIp())) {
									log.info("contains");
									sendSegmentToClient(chunkObj, 0, fileReq.getClientIp(), fileReq.getAgentIp());
								}
								else {
									log.info("else");
									ArrayList<InetAddress> chunkAgentList = chunkObj.getAgentList();
									
									for(InetAddress agentIP: chunkAgentList) {
										log.info("agent address: "+agentIP.getHostAddress());
										WiCacheAgent chunkAgentObj = agentList.get(agentIP);
										log.info("agent: "+chunkAgentObj.getChunkCachedList().size());
										if(chunkAgentObj.getChunkCachedList().containsKey(chunkObj.getChunkName())) {
											log.info("App: Sending file: "+chunkObj.getChunkName()+" "+fileReq.getClientIp().getHostAddress()+" "+chunkAgentObj.getAgentAddr().getHostAddress());
											sendSegmentToClient(chunkObj, 0, fileReq.getClientIp(), chunkAgentObj.getAgentAddr());
										}
										
									}	
									
								}//else
								
							}// for WiCacheFileChunk			
							
						}//if !fileReq.equals
						
					}//while true 
			
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	
		};
		
		new Thread(thread).start();
		
	}//public void sequential()
	
	/*
	 * getFileRequestObject: returns first entry of file request queue
	 */
	
	public WiCacheFileRequest getFileRequestObject(){
		
		if(fileRequestQueue.isEmpty()) {
			return null;
		}
		
		log.info("inside queue"); 
		return fileRequestQueue.removeFirst(); //removes and return the first element 
		
						
	}
	
	/*
	 * ClientChunkEntry: this the data structure used 
	 * to make a chunk entry that is being sent to a client
	 */
	class ClientChunkEntry{
		
		private WiCacheFileChunk chunkObj;
		private long skipIndex;
		private InetAddress clientAddr;
		private InetAddress agentAddr;
		
		public ClientChunkEntry(WiCacheFileChunk chunkObj, long skipIndex, InetAddress clientAddr, InetAddress agentAddr) {
			// TODO Auto-generated constructor stub
			this.chunkObj = chunkObj;
			this.skipIndex = skipIndex;
			this.clientAddr = clientAddr;
			this.agentAddr = agentAddr;
		}
		
		public WiCacheFileChunk getChunkObj() {
			return this.chunkObj;
		}
		
		public long getSkipIndex() {
			return this.skipIndex;
		}
		
		public InetAddress getClientAddr() {
			return this.clientAddr;
		}
		
		public InetAddress getAgentAddr() {
			return this.agentAddr;
		}
		
	}
	
	/*
	 * sendToClientThread: sends the file  to the client 
	 */
	public void sendToClientThread(String fileName, InetAddress clientAddr) {
		
		Runnable thread = new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				try {
					Thread.sleep(1000);
					
					
				} catch (Exception e) {
					e.printStackTrace();
				}			
				
				try{				
					
					log.info("inside thread");
					//log.info("Size: "+fileRequestQueue.size());
					//log.info("file name: "+getFileRequestObject().getFileName());
					while(!wicacheDownloadList.containsKey(clientAddr)) {
						continue;
					}	
					
					log.info("after thread");
					LinkedList<ClientChunkEntry> clientQueue = wicacheDownloadList.get(clientAddr);
					
					WiCacheFile fileObj = wicacheFileList.get(fileName);
					int noOfChunks = fileObj.getChunkList().size();
					
					int i = 0;
					while(i<noOfChunks) {						
						
						ClientChunkEntry objEntry = clientQueue.removeFirst();
						WiCacheFileChunk chunkObj = objEntry.getChunkObj();
						InetAddress agentAddr = objEntry.getAgentAddr();
						long skipIndex = objEntry.getSkipIndex();
						
						String chunkName = chunkObj.getChunkName();
						String fileName = chunkObj.getFileName();
						long chunkSize = chunkObj.getChunkSize();						
						
						//update agent object, agent list
						ChunkDownloadEntry downloadEntry = new ChunkDownloadEntry(clientAddr, chunkName, fileName, chunkSize);
						wicacheAgentList.get(agentAddr).addDownload(clientAddr, downloadEntry);
						wicacheAgentList.get(agentAddr).getChunkCachedList().get(chunkObj.getChunkName()).incHitCount(); //local
						
						//update chunk object, file object, file list
						wicacheFileList.get(chunkObj.getFileName()).getChunkList().get(chunkObj.getChunkNo()).incChunkHitCount();
						
						//we have to fetch the latest info of clients and agents
						HashSet<OdinClient> clientList = new HashSet<OdinClient>(getClients());
						//HashSet<InetAddress> agentList = new HashSet<InetAddress>(getAgents());
						
						//get the client object from client list
						Iterator<OdinClient> clientIter = clientList.iterator();
						OdinClient clntObj = null;
						while(clientIter.hasNext()){
							clntObj = clientIter.next();
							if(clntObj.getIpAddress().equals(clientAddr)){
								
								log.info("sendToClient: Client object found!");
								break;
							}
						}						
						
						//switch or fetch from another AP if necessary
						//check if the given agent is same as the current agent of the client
						String cachedStatus = "CACHED";
						String neighborAP = null;
						InetAddress curAgentAddr =  clntObj.getLvap().getAgent().getIpAddress();
						if(!curAgentAddr.equals(agentAddr)){
							
							//handoffClientToAp(clntObj.getMacAddress(), agentAddr);
							cachedStatus = "NOT_CACHED";
							neighborAP = agentAddr.getHostAddress();
							
						}
						
						//retrieve the socket handle for the agent
						Socket agntSock = agntSockMap.get(curAgentAddr);
						PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());		
						
						//instructs the agent to send the file
						//agents does not know about fragments therefore consider chunks as a full file
						agntWrtr.write("SEND_SEG" +" "+chunkName+" "+chunkSize+" "+skipIndex+" "+clientAddr.getHostAddress()+" "+cachedStatus+" "+neighborAP+" ");
						log.info("SEND_SEG" +" "+chunkName+" "+chunkSize+" "+skipIndex+" "+clientAddr.getHostAddress()+" "+cachedStatus+" "+neighborAP+" ");
						
						agntWrtr.flush();	
						
						//listen for udp update
						//message format: <FILE_SENT> <chunkName> <clientAddr> <agentAddr>
						
						byte[] updateData = new byte[4096];
						DatagramPacket updatePackt = new DatagramPacket(updateData, updateData.length);					
						
						udpSock.receive(updatePackt);
						String updateStr = new String(updatePackt.getData());
						String[] tokens = updateStr.split("\\s+");						
						
						while(!(tokens[0].equals("FILE_SENT")&&tokens[1].equals(chunkObj.getChunkName())
								&&tokens[2].equals(clientAddr.getHostAddress()))){ //&&tokens[3].equals(agentAddr.getHostAddress()))) {
							
							
							
							udpSock.receive(updatePackt);
							updateStr = new String(updatePackt.getData());
							tokens = updateStr.split("\\s+");
							
						}
						
						log.info("update received");
						
						i++;
						
					}//while(i<noOfChunks)
					
					//remove the download entry for client after downloading
					
					if(wicacheDownloadList.containsKey(clientAddr)) {
						wicacheDownloadList.remove(clientAddr);
					}					
					
				}catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		};
		
		new Thread(thread).start();
		
	}

	/*
	 * sendChunkToClient: sends the chunk/segment to the client 
	 */
	public void sendSegmentToClient(WiCacheFileChunk chunkObj, long skipIndex, InetAddress clientAddr, InetAddress agentAddr){
		
		//Runnable thread = new Runnable() {
			
			//@Override
			//public void run() {
				// TODO Auto-generated method stub
				
				try{				
					
					ClientChunkEntry objEntry = new ClientChunkEntry(chunkObj, skipIndex, clientAddr, agentAddr);
					
					//if entry existed for the client
					if(wicacheDownloadList.containsKey(clientAddr)) {
						
						wicacheDownloadList.get(clientAddr).add(objEntry);
						
					}
					//if entry does not exists for the client
					else {
						
						LinkedList<ClientChunkEntry> clientQueue = new LinkedList<ClientChunkEntry>();
						clientQueue.add(objEntry);
						wicacheDownloadList.put(clientAddr, clientQueue);
						
					}						
					
				}catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			//}
		//};
		
		//new Thread(thread).start();
		
	}	
	
	/*
	 * addChunkToCache: add/place the chunk/segment to the cache/AP
	 */
	public void addSegmentToCache(WiCacheFileChunk chunkObj, InetAddress agentAddr, InetAddress srcAddr, int srcPort){
		
		Runnable thread = new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				
				try {			
					
					//retrieve the socket handle for the agent
					Socket agntSock = agntSockMap.get(agentAddr);
					PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());		

					//instructs the agent to add the file					
					agntWrtr.write("ADD_FILE" +" "+chunkObj.getChunkName()+" "+chunkObj.getChunkSize()+" ");
					log.info("ADD_FILE" +" "+chunkObj.getChunkName()+" "+chunkObj.getChunkSize()+" ");

					agntWrtr.flush();	
					
					//update agent object, agent list
					Timestamp timeOfCached = new Timestamp(System.currentTimeMillis());						
					WiCacheChunkEntry chunkEntryObj = new WiCacheChunkEntry(chunkObj.getChunkName(), chunkObj.getFileName(), chunkObj.getChunkSize(), timeOfCached);						
					wicacheAgentList.get(agentAddr).addChunkEntry(chunkObj.getChunkName(), chunkEntryObj);

					//update chunk object, file object, file list												
					wicacheFileList.get(chunkObj.getFileName()).getChunkList().get(chunkObj.getChunkNo()).addAgent(agentAddr);

					//open parent file of file chunk
					FileInputStream fis = new FileInputStream(new File(fileStoragePath+chunkObj.getFileName()));
					DataInputStream dis = new DataInputStream(fis);

					FileOutputStream fos = new FileOutputStream(nfsSharesPath+agentAddr.getHostAddress()+"/"+chunkObj.getChunkName());

					//add/copy/send the file				
					dis.skip(chunkObj.getChunkStartIndex()-1); //starting index is start index - 1
					long remaining = chunkObj.getChunkSize();					
					byte[] buffer = new byte[4096];
					int read =0;

					while(remaining > 0) {
						//log.info("read and write");
						read = dis.read(buffer,0,buffer.length);
						fos.write(buffer,0,read);					
						remaining = remaining - read;
					}

					fos.close();
					dis.close();		

					log.info("addChunkToCache: "+chunkObj.getChunkName()+" added to "+agentAddr+" succesfully");

				}catch(Exception e){
					e.printStackTrace();
				}
				
			}
		};
		
		new Thread(thread).start();
		
	}	
	
	/*
	 * delChunkFromCache: delete/remove the chunk/segment from the cache/AP
	 */
	public void delSegmentFromCache(WiCacheFileChunk chunkObj, InetAddress agentAddr){
			
			Runnable thread = new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					
					try {
						
						//retrieve the socket handle for the agent
						Socket agntSock = agntSockMap.get(agentAddr);
						PrintWriter agntWrtr = new PrintWriter(agntSock.getOutputStream());		

						//instructs the agent to delete the file						
						agntWrtr.write("DEL_FILE" +" "+chunkObj.getChunkName()+" "+chunkObj.getChunkSize()+" ");
						log.info("DEL_FILE" +" "+chunkObj.getChunkName()+" "+chunkObj.getChunkSize()+" ");
						
						
						//update chunk object, file object, file list
						wicacheFileList.get(chunkObj.getFileName()).getChunkList().get(chunkObj.getChunkNo()).delAgent(agentAddr);
						
						//update agent object, agent list
						wicacheAgentList.get(agentAddr).delChunkEntry(chunkObj.getChunkName());					
																	
						File file = new File(nfsSharesPath+agentAddr.getHostAddress()+"/"+chunkObj.getChunkName());
						if(file.delete()) {
							log.info("delChunkFromCache: File "+chunkObj+" deleted successfully");
						}
						else {
							log.info("delChunkFromCache: File "+chunkObj+" delete failed");
						}						
						
					} catch (Exception e) {
						// TODO: handle exception
					}
					
				}
			};
			
			new Thread(thread).start();
			
		}
	
	/*
	 * getAgentChunkList: returns fileCachedList of an agent
	 */
	
	public ConcurrentHashMap<String, WiCacheChunkEntry> getAgentChunkList(InetAddress agentAddr){
		
		if(!wicacheAgentList.containsKey(agentAddr)) {
			log.info("getAgentChunkList error: agentAddr does not exists");
			return null;
		}
		
		return wicacheAgentList.get(agentAddr).getChunkCachedList();
	}
	
	/*
	 * getAgentDownloadList: returns downloadList of an agent 
	 */
	
	public ConcurrentHashMap<InetAddress, ChunkDownloadEntry> getAgentDownloadList(InetAddress agentAddr){
		
		if(!wicacheAgentList.containsKey(agentAddr)) {			
			log.info("getAgentDownloadList error: agentAddr does not exists");
			return null;
		}
		
		return wicacheAgentList.get(agentAddr).getDownloadList();
	}	
	
	/*
	 *getWiCacheFileObject:  returns WiCacheFile's object for a file
	 */
	
	public WiCacheFile getWiCacheFileObject(String fileName){		
			
		if(!wicacheFileList.containsKey(fileName)) {
			log.info("getWiCacheFileObject error: fileName does not exists");
			return null;
		}
		
		return wicacheFileList.get(fileName);
	}
	
	/*
	 * getWiCacheFileChunkObject: returns WiCacheFileChunk's object for a chunk
	 */
	
	public WiCacheFileChunk getWiCacheFileChunkObject(String chunkName){
		
		int index = chunkName.indexOf('_');
		String fileName = chunkName.substring(0, index);
		WiCacheFile fileObj = wicacheFileList.get(fileName);
		
		int i = 0;
		while(i<fileObj.getChunkList().size()){
			
			if(fileObj.getChunkList().get(i).getChunkName().equals(chunkName)) {
				return fileObj.getChunkList().get(i);
			}			
			i++;			
		}	
		
		log.info("getWiCacheFileChunkObject error: chunkName does not exists");
		return null;
		
	}
	
	/*
	 * getWiCacheAgent: returns WiCacheAgent's object for an agent address
	 */
	
	public WiCacheAgent getWiCacheAgent(InetAddress agentAddr){
		
		if(!wicacheAgentList.containsKey(agentAddr)) {
			log.info("getWiCacheAgent error: agentAddr does not exists");
			return null;
		}
		
		return wicacheAgentList.get(agentAddr);
	}
	
	/*
	 *getWiCacheAgentList:  returns list of WiCacheAgent objects
	 */
	
	public ConcurrentHashMap<InetAddress, WiCacheAgent> getWiCacheAgentList(){
		return wicacheAgentList;
	}
	
	/*
	 * getWiCacheFileList: returns list of WiCacheFile objects
	 */
	
	public ConcurrentHashMap<String, WiCacheFile> getWiCacheFileList(){
		return wicacheFileList;
	}			
	
	/*
	 * handleMobility: handles the handoff for 
	 * mobile clients
	 */
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
