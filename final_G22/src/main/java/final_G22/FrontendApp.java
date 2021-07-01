package final_G22;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spark.ModelAndView;
import spark.template.velocity.VelocityTemplateEngine;

import org.apache.log4j.Logger;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class FrontendApp {
	static final int NUM_OF_THREADS = 20;
	static final Logger logger = Logger.getLogger(FrontendApp.class);
	static AmazonDynamoDB client;
	static JSONParser parser = new JSONParser();
	static int totalFiles;
	static Map<String, Double> pageRank = new HashMap<>();
	static boolean isDebug = false;
	
	public static void main(String[] args) {
		staticFiles.location("/public");
		port(8081);
		threadPool(NUM_OF_THREADS);
		
		System.out.println("Indexer Main starts!");
		if(args.length < 1) {
			System.err.println("You need to provide the total number of files.");
			System.exit(1);
		}
		totalFiles = Integer.parseInt(args[0]);
		if (args.length == 2) {
			isDebug = true;
		}
		
		File pageRankDir = new File("pagerankscores");
		File[] pageRankFiles = pageRankDir.listFiles();
		// System.out.println(pageRankFiles.length);
		
		for (int i = 0; i < pageRankFiles.length; i++) {
			try(FileReader fr = new FileReader(pageRankFiles[i]);
					BufferedReader br = new BufferedReader(fr);) 
				{
					String line;
					while ((line = br.readLine()) != null) {
					    line = line.substring(1, line.length()-1);
					    String[] pair = line.split(",");
					    double val = 1;
					    try {
					    	val = Double.parseDouble(pair[1]);
					    } catch (NumberFormatException e) {
					    	// System.out.println(line);
					    }
					    pageRank.put(pair[0], val);
					}
					    
				} catch (IOException e) {
					e.printStackTrace();
				} 
		}
		// System.out.println("PageRank size: " + pageRank.size());

		
//		get("/", (request,response) -> {
//			return "<html><body>"
//					+ "<form action=\"/tfidf\" method=\"POST\">"
//					+ "<label for=\"name\">Search: </label>"
//					+ "<input type=\"text\" name=\"query\" required/><br>"
//					+ "<input type=\"submit\" value=\"Search\"/>"
//					+ "</form></body></html>";
//		});
		
//		get("/tfidf", (request,response) -> {
//			String query = request.queryParams("query");
//			String [] keys = query.split(" ");
//			System.out.println("query:"+query);
//			System.out.println("key number:"+keys.length);
//			Map<String, String> invertedlist = getDataFromDynamo(keys);
//			
//	        HashMap<String, Double> ret = comupteTFIDF(invertedlist);
//			return ret.toString();
//		});
		
		get("/", (req, res) -> {
			Map<String, Object> model = new HashMap<>();
			
		    return new VelocityTemplateEngine().render(
		        new ModelAndView(model, "public/splash.vm")
		    );
		});
		
		get("/search", (req, res) -> {
			String key = req.queryParams("key");
			String page = req.queryParams("page");
			
			if (key == null || key.isEmpty()) {
				res.redirect("/");
				return null;
			}
			
			boolean isFirstPage = false;
			boolean isLastPage = false;
			
			int pageNum; 
			if (page == null || page.isEmpty()) {
				pageNum = 1;
			} else {
				pageNum = Integer.parseInt(page);
			}
			List<String> keys = new ArrayList<String>();
			try {
				Pattern pattern = Pattern.compile("[\\p{Alnum}]+");
				Matcher matcher = pattern.matcher(key);
				
				while(matcher.find()) {
					String keyItem = matcher.group().toLowerCase();
					keys.add(keyItem);
				}
				System.out.println("query:"+key);
				System.out.println("key number:"+keys.size());
				
			} catch (Exception e) {
				e.printStackTrace();
			}

			Map<String, String> invertedlist = getDataFromDynamo(keys);
//			System.out.println(ret.toString());
			Map<String, Double> urlDfidfMap = comupteTFIDF(invertedlist);

//			try {
//				for (Entry<String, String> entry : invertedlist.entrySet()) {
//					String map = entry.getValue();
//					map = map.substring(1, map.length()-1);
//					String[] pairs = map.split(",");
//					for (String pair : pairs) {
//						String[] tuple = pair.split(":");
//						String url = tuple[0];
//						url = url.substring(1, url.length()-1);
//						url = URLDecoder.decode(url, "UTF-8");
//						String tfidfStr = tuple[1];
//						// System.out.println(tfidfStr);
//						double tfidf = (new BigDecimal(tfidfStr)).doubleValue();
//						if (urlDfidfMap.containsKey(url)) {
//							urlDfidfMap.put(url, urlDfidfMap.get(url) + tfidf);
//						} else {
//							urlDfidfMap.put(url, tfidf);
//						}
//					}
//				}
//			} catch (Exception e) {
//				e.printStackTrace();
//			}

			
			if ((urlDfidfMap.size() < (pageNum -1) * 10) || pageNum < 1) {
				pageNum = 1;
			}

			List<String> ranked = getRanked(urlDfidfMap, pageNum);
			List<LinkInfo> rankedLinks = new LinkedList<>();
			try {
				for (String link : ranked) {
					double tfidf = urlDfidfMap.get(link);
					LinkInfo info = new LinkInfo(link, tfidf);
					
					double pageRankScore = 1;
					if (pageRank.containsKey(link)) {
						pageRankScore = pageRank.get(link);
					} 
					info.setPageRank(pageRankScore);
					
					double weightTfidf = 0.99;
					double weightPageRank = 0.01;
					
					double harmonic = ((weightTfidf + weightPageRank) / ((weightTfidf/tfidf) + (weightPageRank/pageRankScore)));
				
					info.setHarmonic(harmonic);
					
					Document doc = Jsoup.connect(link).get();
					info.setTitle(doc.title());
					info.setBody(doc.body().text().substring(0, 70) + "...");
				
					rankedLinks.add(info);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (pageNum == 1) {
				isFirstPage = true;
			} 
			if (ranked.size() != 10) {
				isLastPage = true;
			}
			
			Map<String, Object> model = new HashMap<>();
			model.put("ranked", rankedLinks);
			model.put("isFirstPage", isFirstPage);
			model.put("isLastPage", isLastPage);
			model.put("key", key);
			model.put("page", pageNum);
			model.put("isDebug", isDebug);
			model.put("prev", pageNum - 1);
			model.put("next", pageNum + 1);
			
		    return new VelocityTemplateEngine().render(
		        new ModelAndView(model, "public/search.vm")
		    );
		});
		
	}
	
	private static List<String> getRanked(Map<String, Double> map, int page) {
		List<String> ranked = new LinkedList<>();
		Map<String, Double> copy = new HashMap<>();
		
		for (Entry<String, Double> mapEntry : map.entrySet()) {
			String link = mapEntry.getKey();
			double tfidf = mapEntry.getValue();
			double pageRankScore = 1;
			if (pageRank.containsKey(link)) {
				pageRankScore = pageRank.get(link);
			}
			
			double weightTfidf = 0.99;
			double weightPageRank = 0.01;
			
			double harmonic = ((weightTfidf + weightPageRank) / ((weightTfidf/tfidf) + (weightPageRank/pageRankScore)));
			// System.out.println("tfidf: " + tfidf + ", pageRank: " + pageRankScore + ", harmonic: " + harmonic);
			copy.put(link, harmonic);
		}
		 
		//Use Comparator.reverseOrder() for reverse ordering
		copy.entrySet()
		    .stream()
		    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())) 
		    .forEachOrdered(x -> ranked.add(x.getKey()));

		List<String> partitionedRanked = ranked.subList((page-1)*10, Math.min(page*10, ranked.size()));
		
		return partitionedRanked;
	}
	

	public static Map<String,String> getDataFromDynamo(List<String> keys) {
		//load credential
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			System.out.println("Update Credential");
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. " +
							"Please make sure that your credentials file is at the correct " +
							"location (~/.aws/credentials), and is in valid format.",
							e);
		}	
		
		try {
		//create client
		client = AmazonDynamoDBClientBuilder.standard()
	      .withCredentials(credentialsProvider)
	      .withRegion("us-east-1")
	      .build();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		DynamoDB dynamoDB = new DynamoDB(client);
		Table table = dynamoDB.getTable("InvertedIndex");

		//word, {doc:tf, doc:tf}
		Map<String,String> invertedlist = new HashMap<String,String>();
		PorterStemmer s = new PorterStemmer();
		for(String key : keys) {
			//stem search key
			boolean allLetters = true;
			for(int i=0; i<key.length(); i++) {
				char ch = key.charAt(i);
				if(Character.isLetter(ch)) {
					s.add(ch);
				}
				else {
					s.reset();
					allLetters = false;
					break;	
				}
			}
			if(allLetters){
				s.stem();
				key = s.toString();
				s.reset();
			}
		Item item = null;
		try {
			item = table.getItem("word", key); 
		} catch (Exception e) {
			e.printStackTrace();
		}
    	
    	
    	if(item == null) {
    		System.out.println("Item " + key + " does not exsit");
    		continue;
    	}
    	String json = item.toJSON();
    	try {
    		Object obj = parser.parse(json);
    		HashMap<String, String> map = (HashMap<String, String>)obj;
    		String info = map.get("info");
     			
    		System.out.println(key+" "+info);
    		invertedlist.put(key, info);
     		
     		
     		
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}  
     	
    }
//    
//     
    return invertedlist;
		
	}
	
	public static HashMap<String, Double> comupteTFIDF( Map<String,String> invertedlist) throws IOException {
		//docid: scores
		HashMap<String, Double> scorings = new HashMap<String, Double>();
		for(String word: invertedlist.keySet()) {
			String info = invertedlist.get(word);
			//read large info from s3 files
			if(info.startsWith("s3://")) {
				String bucket_name = "testmyindexer";//this can be changed
				final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
				S3Object o = s3.getObject(bucket_name, "storage/"+word);
				if(o == null) {
					System.out.println("Cannot find object in s3");
					continue;
				}
				S3ObjectInputStream s3is = o.getObjectContent();
        InputStreamReader streamReader = new InputStreamReader(s3is);
        BufferedReader reader = new BufferedReader(streamReader);
        info = ""; 
        String line;
        if((line=reader.readLine())!=null) {
        	info = line;
        }
			}
			Object obj;
			try {
				obj = parser.parse(info);
				HashMap<String, Double> map = (HashMap<String, Double>)obj;
				for(String docid: map.keySet()) {
					String link = URLDecoder.decode(docid, "UTF-8");
					double tf = (0.4+(1-0.4)* map.get(docid));
					double idf = Math.log((double)totalFiles/(double)map.size());
					double score = scorings.getOrDefault(docid, (double) 0);
					score += tf*idf;
					scorings.put(link,score);
					
				}
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
   		
		}
		
		return scorings;
	}
	
	
	 
}
