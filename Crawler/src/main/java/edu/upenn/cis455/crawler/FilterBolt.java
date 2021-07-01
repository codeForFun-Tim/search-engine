package edu.upenn.cis455.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
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
import edu.upenn.cis455.crawler.info.URLInfo;

public class FilterBolt implements IRichBolt {
	static Logger log = Logger.getLogger(FilterBolt.class);
	
	Fields schema = new Fields("URLInfo");
	
    String executorId = UUID.randomUUID().toString();
    static AtomicInteger idleCheck = new AtomicInteger();

    private OutputCollector collector;

    public FilterBolt() {
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
		List<URLInfo> URLList = (List<URLInfo>) input.getObjectByField("URLInfoList");
		// log.debug(getExecutorId() + " received list of URLs.");
		
		for (URLInfo URL : URLList) {
			if (!XPathCrawler.visitedLinks.contains(URL.normalize()) && URL.isDesired()) {
				URL urlTask;
				try {
		            urlTask = new URL("http://3.85.142.75:8081/put?url=" + URL.normalize());
		            URLConnection conn = urlTask.openConnection();
		            conn.connect();

		            // open the stream and put it into BufferedReader
		            BufferedReader br = new BufferedReader(
		                               new InputStreamReader(conn.getInputStream()));

		            String inputLine = br.readLine();
		            if (inputLine != null && inputLine.equals("Success")) {
		            	// log.debug(getExecutorId() + " added a link to queue: " + URL.normalize());
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
