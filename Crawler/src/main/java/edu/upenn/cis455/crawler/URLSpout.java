package edu.upenn.cis455.crawler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import edu.upenn.cis.stormlite.OutputFieldsDeclarer;
import edu.upenn.cis.stormlite.TopologyContext;
import edu.upenn.cis.stormlite.routers.IStreamRouter;
import edu.upenn.cis.stormlite.spout.IRichSpout;
import edu.upenn.cis.stormlite.spout.SpoutOutputCollector;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis.stormlite.tuple.Values;
import edu.upenn.cis455.crawler.info.URLInfo;

public class URLSpout implements IRichSpout {
	
	static Logger log = Logger.getLogger(URLSpout.class);
    String executorId = UUID.randomUUID().toString();
    static AtomicInteger idleCheck = new AtomicInteger();

	SpoutOutputCollector collector;
	
    public URLSpout() {
    	log.debug("Starting spout");
    }

	@Override
	public String getExecutorId() {
		return executorId;
	}

	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("URLInfo"));
		
	}

	@Override
	public void open(Map<String, String> config, TopologyContext topo, SpoutOutputCollector collector) {
		this.collector = collector;
	}

	@Override
	public void close() {
		// Nothing special to close
		return;
	}

	@Override
	public void nextTuple() {
		idleCheck.getAndIncrement();
		Queue<URLInfo> taskQueue = XPathCrawler.taskQueue;
		
		URL url;

        try {
            // get URL content

            url = new URL("http://3.85.142.75:8081/next");
            URLConnection conn = url.openConnection();

            // open the stream and put it into BufferedReader
            BufferedReader br = new BufferedReader(
                               new InputStreamReader(conn.getInputStream()));

            String input = br.readLine();
            if (input != null && !input.equals("Empty")) {
            	URLInfo fetched = new URLInfo(input);
            	if (fetched.isDesired()) {
            		taskQueue.add(fetched);
            		System.out.println("Remotely fetched url: " + input);
            	}
            }
			br.close();
        } catch (Exception e) {
           // e.printStackTrace();
        } 
		
		if (!taskQueue.isEmpty() && XPathCrawler.maxNumOfFile != 0) {
			URLInfo nextInfo = taskQueue.poll();
			// log.debug(getExecutorId() + " emitting " + nextInfo.normalize());
			this.collector.emit(new Values<Object>(nextInfo));
		}
		idleCheck.getAndDecrement();
		Thread.yield();
	}

	@Override
	public void setRouter(IStreamRouter router) {
		this.collector.setRouter(router);
	}

}
