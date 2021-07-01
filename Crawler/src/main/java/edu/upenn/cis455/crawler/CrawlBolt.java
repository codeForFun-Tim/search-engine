package edu.upenn.cis455.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import edu.upenn.cis.stormlite.OutputFieldsDeclarer;
import edu.upenn.cis.stormlite.TopologyContext;
import edu.upenn.cis.stormlite.bolt.IRichBolt;
import edu.upenn.cis.stormlite.bolt.OutputCollector;
import edu.upenn.cis.stormlite.routers.IStreamRouter;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis.stormlite.tuple.Tuple;
import edu.upenn.cis.stormlite.tuple.Values;
import edu.upenn.cis455.crawler.info.RobotsTxtInfo;
import edu.upenn.cis455.crawler.info.URLInfo;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.DocInfo;
import edu.upenn.cis455.storage.S3Wrapper;

public class CrawlBolt implements IRichBolt {
	static Logger log = Logger.getLogger(CrawlBolt.class);
	
	Fields schema = new Fields("DocInfo");
	
    String executorId = UUID.randomUUID().toString();
    static AtomicInteger idleCheck = new AtomicInteger();

    private HTTPClient client;
    private OutputCollector collector;
    private DBWrapper myDB;

    public CrawlBolt() {
    	// At this point there must be an instance of myDB initiated
    	this.myDB = DBWrapper.getInstance(null);
    	this.client = XPathCrawler.client;
    }
    
	@Override
	public String getExecutorId() {
		return this.executorId;
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(schema);
	}

	@Override
	public void cleanup() {
		// Do Nothing
	}

	@Override
	public void execute(Tuple input) {
		idleCheck.getAndIncrement();
		URLInfo currTask = (URLInfo) input.getObjectByField("URLInfo");
		// log.debug(getExecutorId() + " received " + currTask.normalize());
		
		// Check for robots.txt rules. If no info, get robots.txt
		String targetHost = currTask.getHostName() + ":" + currTask.getPortNo();
		if (!client.getRulesMap().containsKey(targetHost)) {
			log.debug(getExecutorId() + " obtaining robots.txt for host " + currTask.getHostName());
			String robotsTxt;
			if (currTask.getProtocol().equals("http")) {
				robotsTxt = client.getFileHTTP(new URLInfo(currTask.getProtocol(), currTask.getHostName(), currTask.getPortNo(), "/robots.txt"));
			} else {
				robotsTxt = client.getFileHTTPS(new URLInfo(currTask.getProtocol(), currTask.getHostName(), currTask.getPortNo(), "/robots.txt"));
			}
			if (robotsTxt != null) {
				client.parseRobotsTxt(currTask, robotsTxt);
			}
		} 
		
		RobotsTxtInfo rules = client.getRulesMap().get(targetHost);
		if (rules == null) {
			// If failed to obtain, set a default rule (5-sec delay)
			rules = new RobotsTxtInfo();
		}
		// log.debug(getExecutorId() + " obtained rules for " + currTask.normalize());
		  
		if (XPathCrawler.delayTable.containsKey(targetHost) && XPathCrawler.delayTable.get(targetHost) > System.currentTimeMillis()) {
			// In range of delay, put task back to queue
			XPathCrawler.taskQueue.add(currTask);
			// log.debug(getExecutorId() + " put " + currTask.normalize() + " back to queue due to delay");
			idleCheck.getAndDecrement();
			return;
		}
		  
		// Check if file path is disallowed
		boolean isDisallowed = false;
		for (String disallowed : rules.getDisallowedLinks()) {
			if (currTask.getFilePath().startsWith(disallowed)) {
				isDisallowed = true;
				break;
			}
		}
		if (isDisallowed) {
			// Disallowed path, ignore
			log.debug(getExecutorId() + " found link disallowed: " + currTask.normalize());
			idleCheck.getAndDecrement();
			return;
		}
		  
		// Check if cached
		DocInfo cache = null;
		try {
			cache = myDB.getDocInfo(currTask.normalize());
		} catch (Exception e) {
			e.printStackTrace();
		}
		  
		// Obtain lastModifiedDate
		Date modifiedDate = null;
//		if (cache != null) {
//			modifiedDate = cache.getLastModified();
//		}
		// Remote check if crawled
//		URL url;
//
//        try {
//            url = new URL("http://34.207.162.160:8081/check?url=" + currTask.normalize());
//            URLConnection conn = url.openConnection();
//
//            // open the stream and put it into BufferedReader
//            BufferedReader br = new BufferedReader(
//                               new InputStreamReader(conn.getInputStream()));
//
//            String inputLine = br.readLine();
//            if (inputLine != null && inputLine.equals("true")) {
//            	XPathCrawler.visitedLinks.add(currTask.normalize());
//            	idleCheck.getAndDecrement();
//        		return;
//            }
//        } catch (Exception e) {
//            // e.printStackTrace();
//        } 
		// Update delay table
		XPathCrawler.delayTable.put(targetHost, System.currentTimeMillis() + rules.getCrawlDelay());
		// Update maxNumOfFile
		XPathCrawler.maxNumOfFile--;
		// Update visitedLinks
		XPathCrawler.visitedLinks.add(currTask.normalize());
//		URL url2;
//		try {
//            url2 = new URL("http://34.207.162.160:8081/crawled?url=" + currTask.normalize());
//            URLConnection conn2 = url2.openConnection();
//
//            // open the stream and put it into BufferedReader
//            BufferedReader br2 = new BufferedReader(
//                               new InputStreamReader(conn2.getInputStream()));
//
//            String inputLine = br2.readLine();
//            if (inputLine != null && inputLine.equals("Success")) {
//            	log.debug(getExecutorId() + " added to url-seen: " + currTask.normalize());
//            }
//        } catch (Exception e) {
//            // e.printStackTrace();
//        } 
		// Send HEAD
		DocInfo targetDoc;
		String headResult;
		log.debug(getExecutorId() + " sending head request for " + currTask.normalize());
		if (currTask.getProtocol().equals("http")) {
			headResult = client.sendHeadHTTP(currTask, modifiedDate);
		} else {
			headResult = client.sendHeadHTTPS(currTask, modifiedDate);
		}
		
		if (headResult == HTTPClient.ERROR || headResult == HTTPClient.REDIRECT || headResult == HTTPClient.UNDESIRED) {
			// Ignore these cases. For redirects, already added to end of queue.
			log.debug(getExecutorId() + " ignoring request: (" + headResult + ") " + currTask.normalize());
			idleCheck.getAndDecrement();
			return;
		} else if (headResult == HTTPClient.NOT_MODIFIED) {
			// Not modified. Retrieve from docDB. Print to console
			System.out.println(currTask.normalize() + ": Not modified.");
			log.debug(getExecutorId() + " found link not modified: " + currTask.normalize() + ". Retrieving cache.");
			targetDoc = myDB.getDocInfo(currTask.normalize());
		} else {
			// headResult is mimeType. Go and download the file
			// log.debug(getExecutorId() + " downloading link: " + currTask.normalize());
			System.out.println(currTask.normalize() + ": Downloading.");
			String targetFile;
			if (currTask.getProtocol().equals("http")) {
				targetFile = client.getFileHTTP(currTask);
			} else {
				targetFile = client.getFileHTTPS(currTask);
			}
			if (targetFile == null) {
				// Something went wrong between HEAD and GET. Just ignore and continue
				idleCheck.getAndDecrement();
				return;
			}
			targetDoc = new DocInfo(currTask.normalize(), headResult, targetFile);
		}
		
		if (targetDoc != null) {
			S3Wrapper.uploadToS3(targetDoc);
			// log.debug(getExecutorId() + " emitting doc for " + currTask.normalize());
			this.collector.emit(new Values<Object>(targetDoc));
			idleCheck.getAndDecrement();
			return;
		}
		
		idleCheck.getAndDecrement();
	}

	@Override
	public void prepare(Map<String, String> stormConf, TopologyContext context, OutputCollector collector) {
		this.collector = collector;
		
	}

	@Override
	public void setRouter(IStreamRouter router) {
		this.collector.setRouter(router);
	}

	@Override
	public Fields getSchema() {
		return this.schema;
	}

}
