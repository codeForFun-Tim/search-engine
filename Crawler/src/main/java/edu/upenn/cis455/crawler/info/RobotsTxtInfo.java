package edu.upenn.cis455.crawler.info;

// import java.util.ArrayList;
// import java.util.HashMap;
import java.util.HashSet;

/** (MS1, MS2) Holds information about a robots.txt file.
  */
public class RobotsTxtInfo {
	
	private HashSet<String> disallowedLinks;
	// private HashMap<String,ArrayList<String>> allowedLinks;
	
	private int crawlDelay;
	// private ArrayList<String> sitemapLinks;
	// private ArrayList<String> userAgents;
	
	private boolean isSpecific;
	
	public RobotsTxtInfo() {
		disallowedLinks = new HashSet<String>();
		// allowedLinks = new HashMap<String,ArrayList<String>>();
		crawlDelay = 5000;
		// sitemapLinks = new ArrayList<String>();
		// userAgents = new ArrayList<String>();
		isSpecific = false;
	}
	
	public void addDisallowedLink(String value){
		if (!disallowedLinks.contains(value)) {
			disallowedLinks.add(value);
		}
	}
	
//	public void addAllowedLink(String key, String value){
//		if(!allowedLinks.containsKey(key)){
//			ArrayList<String> temp = new ArrayList<String>();
//			temp.add(value);
//			allowedLinks.put(key, temp);
//		}
//		else{
//			ArrayList<String> temp = allowedLinks.get(key);
//			if(temp == null)
//				temp = new ArrayList<String>();
//			temp.add(value);
//			allowedLinks.put(key, temp);
//		}
//	}
	
	public void addCrawlDelay(int delay){
		crawlDelay = delay;
	}
	
//	public void addSitemapLink(String val){
//		sitemapLinks.add(val);
//	}
//	
//	public void addUserAgent(String key){
//		userAgents.add(key);
//	}
//	
//	public boolean containsUserAgent(String key){
//		return userAgents.contains(key);
//	}
//	
	public HashSet<String> getDisallowedLinks(){
		return this.disallowedLinks;
	}
//	
//	public ArrayList<String> getAllowedLinks(String key){
//		return allowedLinks.get(key);
//	}
	
	public int getCrawlDelay(){
		return this.crawlDelay;
	}
	
	public void reset() {
		this.disallowedLinks = new HashSet<String>();
	}
	
	public boolean isSpecific() {
		return this.isSpecific;
	}
	
	public void setSpecific() {
		this.isSpecific = true;
	}
	
//	public void print(){
//		for(String userAgent:userAgents){
//			System.out.println("User-Agent: "+userAgent);
//			ArrayList<String> dlinks = disallowedLinks.get(userAgent);
//			if(dlinks != null)
//				for(String dl:dlinks)
//					System.out.println("Disallow: "+dl);
//			ArrayList<String> alinks = allowedLinks.get(userAgent);
//			if(alinks != null)
//					for(String al:alinks)
//						System.out.println("Allow: "+al);
//			if(crawlDelays.containsKey(userAgent))
//				System.out.println("Crawl-Delay: "+crawlDelays.get(userAgent));
//			System.out.println();
//		}
//		if(sitemapLinks.size() > 0){
//			System.out.println("# SiteMap Links");
//			for(String sitemap:sitemapLinks)
//				System.out.println(sitemap);
//		}
//	}
	
//	public boolean crawlContainAgent(String key){
//		return crawlDelays.containsKey(key);
//	}
}
