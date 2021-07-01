package edu.upenn.cis455.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import edu.upenn.cis.stormlite.OutputFieldsDeclarer;
import edu.upenn.cis.stormlite.TopologyContext;
import edu.upenn.cis.stormlite.bolt.IRichBolt;
import edu.upenn.cis.stormlite.bolt.OutputCollector;
import edu.upenn.cis.stormlite.routers.IStreamRouter;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis.stormlite.tuple.Tuple;
import edu.upenn.cis.stormlite.tuple.Values;
import edu.upenn.cis455.crawler.info.URLInfo;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.DocInfo;
import edu.upenn.cis455.storage.S3Wrapper;

public class ParserBolt implements IRichBolt {
	static Logger log = Logger.getLogger(ParserBolt.class);
	
	Fields schema = new Fields("URLInfoList");
	
    String executorId = UUID.randomUUID().toString();
    static AtomicInteger idleCheck = new AtomicInteger();

    private OutputCollector collector;
    private DBWrapper myDB;

    public ParserBolt() {
    	// At this point there must be an instance of myDB initiated
    	this.myDB = DBWrapper.getInstance(null);
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
		DocInfo currDoc = (DocInfo) input.getObjectByField("DocInfo");
		log.debug(getExecutorId() + " received doc for link " + currDoc.getNormalizedLink());
		
		// Store the doc to myDB, unless the received object is the same as cache
//		DocInfo cache = null;
//		try {
//			cache = myDB.getDocInfo(currDoc.getNormalizedLink());
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		if (cache == null || !currDoc.equals(cache)) {
//			// Store the info
//			// myDB.addDocInfo(currDoc.getNormalizedLink(), currDoc);
//			S3Wrapper.uploadToS3(currDoc);
//		}
		
		List<URLInfo> URLList = new LinkedList<URLInfo>();
		// Extract URLs
		if (currDoc.getMimeType().contains("text/html")) {
			String htmlStr = currDoc.getContent();
			Document htmlDoc = Jsoup.parse(htmlStr, currDoc.getNormalizedLink());
			Elements links = htmlDoc.select("a[href]");
			  
			// Iterate through all links and see if it's desired
			for (Element link : links) {
				URLInfo linkInfo = new URLInfo(link.attr("abs:href"));
				if (linkInfo.isDesired()) {
					URLList.add(linkInfo);
				}
			}
		}
		
		if (!URLList.isEmpty()) {
			// log.debug(getExecutorId() + " emitting list of URLs for doc with link " + currDoc.getNormalizedLink());
			this.collector.emit(new Values<Object>(URLList));
			
			for (URLInfo info : URLList) {
				URL urlTask;
				try {
		            urlTask = new URL("http://3.85.142.75:8081/out?url=" + currDoc.getNormalizedLink() + "&dest=" + info.normalize());
		            URLConnection conn = urlTask.openConnection();
		            conn.connect();

		            // open the stream and put it into BufferedReader
		            BufferedReader br = new BufferedReader(
		                               new InputStreamReader(conn.getInputStream()));

		            String inputLine = br.readLine();
		            if (inputLine != null && inputLine.equals("Success")) {
		            	// log.debug(getExecutorId() + " added a link to url-map: " + info.normalize());
		            }
					br.close();
		        } catch (Exception e) {
		            // e.printStackTrace();
		        } 
			}
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
