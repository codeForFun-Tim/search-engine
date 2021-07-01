package edu.upenn.cis455.Indexer.EMRIndexer;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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

/**
 * An example TFIDF computation and web interface program
 **/
public class IndexMain {
	static final Logger logger = Logger.getLogger(IndexMain.class);
	static AmazonDynamoDB client;
	static JSONParser parser = new JSONParser();
	static int totalFiles;
	
	public static void main(String[] args) {
		System.out.println("Indexer Main starts!");
		if(args.length < 1) {
			System.err.println("You need to provide the total number of files.");
			System.exit(1);
		}
		totalFiles = Integer.parseInt(args[0]);
		
		port(8000);
		
		get("/", (request,response) -> {
			return "<html><body>"
					+ "<form action=\"/tfidf\" method=\"POST\">"
					+ "<label for=\"name\">Search: </label>"
					+ "<input type=\"text\" name=\"query\" required/><br>"
					+ "<input type=\"submit\" value=\"Search\"/>"
					+ "</form></body></html>";
		});
		
		post("/tfidf", (request,response) -> {
			String query = request.queryParams("query");
			Pattern pattern = Pattern.compile("[\\p{Alnum}]+");
			Matcher matcher = pattern.matcher(query);
			List<String> keys = new ArrayList<String>();
			while(matcher.find()) {
				String key = matcher.group().toLowerCase();
				keys.add(key);
			}
			System.out.println("query:"+query);
			System.out.println("key number:"+keys.size());
			Map<String, String> invertedlist = getDataFromDynamo(keys);
			
	        HashMap<String, Double> ret = comupteTFIDF(invertedlist);
			return ret.toString();
		});
			
	}
	
	public static Map<String,String> getDataFromDynamo(List<String> keys) {
		//load credential
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        
        //create client
		client = AmazonDynamoDBClientBuilder.standard()
	            .withCredentials(credentialsProvider)
	            .withRegion("us-east-1")
	            .build();
			
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
        	Item item = table.getItem("word", key); 

        	if(item == null) {
        		System.out.println("Item " + key + " does not exsit");
        		continue;
        	}
        	String json = item.toJSON();
        	try {
        		Object obj = parser.parse(json);
        		HashMap<String, String> map = (HashMap<String, String>)obj;
        		String info = map.get("info");
        			
        		//System.out.println(key+" "+info);
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
					double tf =  (0.4+(1-0.4)* map.get(docid));
					double idf = Math.log((double)totalFiles/(double)map.size());
					double score = scorings.getOrDefault(docid, (double) 0);
					score += tf*idf;
					scorings.put(docid,score);
					
				}
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
		}
		
		return scorings;
	}
	
	
	 
}
