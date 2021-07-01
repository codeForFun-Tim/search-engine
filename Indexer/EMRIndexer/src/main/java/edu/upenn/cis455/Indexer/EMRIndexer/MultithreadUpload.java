package edu.upenn.cis455.Indexer.EMRIndexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.json.simple.JSONObject;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
/**
 *A multithreaded program to upload data from S3 to DynamoDB. 
 */
public class MultithreadUpload {
	static String bucket_name;
	static AmazonDynamoDB client;
	static AmazonS3 s3;
	
	public static void main(String[] args) {
		final String USAGE = "\n" +
				"To run this example, supply the name of a bucket to list!\n" +
				"\n" +
				"Ex: ListObjects <bucket-name>\n";

		if (args.length < 1) {
			System.out.println(USAGE);
			System.exit(1);
		}

		bucket_name = args[0];

		System.out.format("Objects in S3 bucket %s:\n", bucket_name);

		s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
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
		client = AmazonDynamoDBClientBuilder.standard()
				.withCredentials(credentialsProvider)
				.withRegion("us-east-1")
				.build();


		ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucket_name).withPrefix("output/");
		ListObjectsV2Result listing = s3.listObjectsV2(req);

		//thread pool
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
		for (S3ObjectSummary os: listing.getObjectSummaries()) {
			System.out.println("* " + os.getKey());
			if(!os.getKey().startsWith("output/part")) {
				continue;
			}
			Task task = new Task(os);


			executor.execute(task);
		}
		executor.shutdown();


	}
	/*Upload data to dynamodb*/
	public static class Task implements Runnable {
		private S3ObjectSummary os;

		public Task(S3ObjectSummary os) {
			this.os = os;
		}

		public void run() {
			try {
				//read from an s3 file
                S3Object o = s3.getObject(bucket_name, os.getKey());
                S3ObjectInputStream s3is = o.getObjectContent();
                
                InputStreamReader streamReader = new InputStreamReader(s3is);
                BufferedReader reader = new BufferedReader(streamReader);
                String line;
                while((line=reader.readLine())!=null) {
                	//parse each line of the file
                	String[] splits = line.split("\\s+");
                	Map<String,AttributeValue> attributeValues = new HashMap<>();
                	attributeValues.put("word", new AttributeValue().withS(splits[0]));
                	Map<String,Float> map = new HashMap<String, Float>();
                	for(int i = 1; i< splits.length; i+=2) {
                		map.put(splits[i],Float.parseFloat(splits[i+1]));
                	}
                	JSONObject text = new JSONObject(map);
                	String jsonText = text.toString();
                	//long threadId = Thread.currentThread().getId();
                	//System.out.println(threadId+" "+splits[0]+"   "+jsonText);
                	
                	//aws dynamodb item limit
                	if(jsonText.length()<400000-1000) {
                		attributeValues.put("info", new AttributeValue().withS(jsonText));
                	}
                	else {
                		System.out.println("storage/"+splits[0]);
                		s3.putObject(bucket_name, "storage/"+splits[0], jsonText);
                		attributeValues.put("info", new AttributeValue().withS("s3://testmyindexer/storage/"+splits[0]));
                	}
                	
                	PutItemRequest putItemRequest = new PutItemRequest()
                            .withTableName("InvertedIndex")
                            .withItem(attributeValues);
                    PutItemResult putItemResult = client.putItem(putItemRequest);
                    
                	
                }

                s3is.close();
                
            } catch (AmazonServiceException e) {
                System.err.println(e.getErrorMessage());
                System.exit(1);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            
            System.out.println("Done!");
		}
	}
}