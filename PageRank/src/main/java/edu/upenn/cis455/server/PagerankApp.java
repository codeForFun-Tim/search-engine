package edu.upenn.cis455.server;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class PagerankApp {
	
	AmazonS3 s3;
	
	public void init() {
	    s3 = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();;
	  }

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
//		if(args.length < 2) {
//			System.err.println("You need to provide access key and secret access key");
//			System.exit(1);
//		}
		
		PagerankApp s3Reader = new PagerankApp();
		
	    s3Reader.init();
	    		
		port(8005);
		
		get("/", (request,response) -> {
			return "<html><body>PageRank App running</body></html>";
		});
		
		get("/pagerank", (request,response) -> {
			System.out.println("Pagerank called!");
			try {
				List<String> keys = s3Reader.getObjectslistFromFolder("pagerank4596", "Pagerank/old2/output/");
				String msg = s3Reader.readFromS3("pagerank4596", keys);
			
			
				return msg;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return "";
		});
		
	}
	
	public String readFromS3(String bucketName, List<String> keys) throws IOException {
		String msg = "";
		for (String key : keys)
		{
			System.out.println(key);
		    S3Object s3object = s3.getObject(new GetObjectRequest(
		            bucketName, key));
		    System.out.println(s3object.getObjectMetadata().getContentType());
		    System.out.println(s3object.getObjectMetadata().getContentLength());
	
		    BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()));
		    String line;
		    
		    while((line = reader.readLine()) != null) {
		      // can copy the content locally as well
		      // using a buffered writer
		      msg = msg + line +"\r\n";
		    }
		}
		System.out.println("Returning message");
	    return msg;
	  }
	
	public List<String> getObjectslistFromFolder(String bucketName, String folderKey) {
		   
		System.out.println("Getting list of contents of folder");
//		  ListObjectsRequest listObjectsRequest = 
//		                                new ListObjectsRequest()
//		                                      .withBucketName(bucketName)
//		                                      .withPrefix(folderKey + "/");
//		 
		  List<String> keys = new ArrayList<>();
//		 
//		  ObjectListing objects = s3.listObjects(listObjectsRequest);
//		  for (;;) {
//		    List<S3ObjectSummary> summaries = objects.getObjectSummaries();
//		    if (summaries.size() < 1) {
//		      break;
//		    }
//		    summaries.forEach(s -> keys.add(s.getKey()));
//		    objects = s3.listNextBatchOfObjects(objects);
//		  }
		  
		  ObjectListing objectListing = s3.listObjects(bucketName);
		  for(S3ObjectSummary os : objectListing.getObjectSummaries()) {
			  if(os.getKey().startsWith(folderKey))
			  {
				  keys.add(os.getKey());
			  }
		  }
		 System.out.println("Keys + "+keys);
		  return keys;
		}

}
