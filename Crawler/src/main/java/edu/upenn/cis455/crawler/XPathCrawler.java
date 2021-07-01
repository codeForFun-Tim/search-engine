package edu.upenn.cis455.crawler;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.upenn.cis.stormlite.Config;
import edu.upenn.cis.stormlite.LocalCluster;
import edu.upenn.cis.stormlite.Topology;
import edu.upenn.cis.stormlite.TopologyBuilder;
import edu.upenn.cis.stormlite.tuple.Fields;
import edu.upenn.cis455.crawler.info.RobotsTxtInfo;
import edu.upenn.cis455.crawler.info.URLInfo;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.DocInfo;
import test.edu.upenn.cis.stormlite.PrintBolt;
import test.edu.upenn.cis.stormlite.WordCounter;
import test.edu.upenn.cis.stormlite.WordSpout;

/** (MS1, MS2) The main class of the crawler.
  */
public class XPathCrawler {
	private static String initURL;
	private static String storePath;
	private static int maxFileSize;
	public static int maxNumOfFile = -1;
	private static String monitorHost = "cis455.cis.upenn.edu";
	
	public static HashMap<String, Long> delayTable = new HashMap<String, Long>();
	public static HashSet<String> visitedLinks = new HashSet<String>();
	public static ConcurrentLinkedQueue<URLInfo> taskQueue = new ConcurrentLinkedQueue<URLInfo>();
	public static HTTPClient client;
	
	private static DBWrapper myDB;
	
	private static final String URL_SPOUT = "url_spout";
	private static final String CRAWL_BOLT = "crawl_bolt";
	private static final String PARSER_BOLT = "parser_bolt";
	private static final String FILTER_BOLT = "filter_bolt";
	
  public static void main(String args[])
  {
	  if (args.length < 3) {
	      System.err.println("Missing arguments!");
	      System.exit(1);
	  } else if (args.length > 5) {
		  System.err.println("Too many arguments!");
	      System.exit(1);
	  }
	  initURL = args[0];
	  storePath = args[1];
	  maxFileSize = Integer.parseInt(args[2]) * 1000000; // 1 megabyte = 10^6 bytes
	  
	  if (args.length >= 4) {
		  maxNumOfFile = Integer.parseInt(args[3]);
	  }
	  
	  if (args.length == 5) {
		  monitorHost = args[4];
	  }
	  
	  // Create db dir if not exists
	  File file = new File(storePath);
	  if (!file.exists()) {
		  file.mkdir();
	  }
	  
	  // Initiate DB
	  try {
		  myDB = DBWrapper.getInstance(storePath);
	    	
	  } catch (Exception e) {
		  e.printStackTrace();
		  return;
	  }
	  
	  // If url encoded, try decode
	  try {
		  initURL = URLDecoder.decode(initURL, StandardCharsets.UTF_8.name());
	  } catch (Exception e) {
		  // Do nothing
	  }
	  
	  // Initiate client
	  client = new HTTPClient(maxFileSize, monitorHost);
	  
	  URLInfo initTask = new URLInfo(initURL);
	  if (initTask.isDesired()) {
		  taskQueue.add(initTask);
	  }
	  
	  // Setting up StormLite
	  Config config = new Config();

      URLSpout spout = new URLSpout();
      CrawlBolt crawlBolt = new CrawlBolt();
      ParserBolt parserBolt = new ParserBolt();
      FilterBolt filterBolt = new FilterBolt();
      
      // Building Topology
      TopologyBuilder builder = new TopologyBuilder();
      builder.setSpout(URL_SPOUT, spout, 1);
      builder.setBolt(CRAWL_BOLT, crawlBolt, 10).shuffleGrouping(URL_SPOUT);
      builder.setBolt(PARSER_BOLT, parserBolt, 10).shuffleGrouping(CRAWL_BOLT);
      builder.setBolt(FILTER_BOLT, filterBolt, 5).shuffleGrouping(PARSER_BOLT);

      // Printing topology
      LocalCluster cluster = new LocalCluster();
      Topology topo = builder.createTopology();

      ObjectMapper mapper = new ObjectMapper();
		try {
			String str = mapper.writeValueAsString(topo);
			
			System.out.println("The StormLite topology is:\n" + str);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
      
      cluster.submitTopology("crawler", config, 
      		builder.createTopology());
	  
      // Checking for condition
      boolean isAllIdle = false;
	  while (!isAllIdle) {
		  if ((taskQueue.isEmpty() || maxNumOfFile == 0) && URLSpout.idleCheck.get() == 0 && CrawlBolt.idleCheck.get() == 0
				  && ParserBolt.idleCheck.get() == 0 && FilterBolt.idleCheck.get() == 0) {
			  isAllIdle = true;
		  }
		  try {
			  // Allow router to send info
			  Thread.sleep(1000);
		  } catch (InterruptedException e) {
			  // Ignore
		  }
	  }
	  System.out.println("Crawl finished.");
	  cluster.killTopology("crawler");
      cluster.shutdown();
	  System.exit(0);
  }
  
  public ConcurrentLinkedQueue<URLInfo> getFrontierQueue() {
	  return taskQueue;
  }
	
}
