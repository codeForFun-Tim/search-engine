package edu.upenn.cis455.Indexer.EMRIndexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class TestS3GetItem {

	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		String word = "'ll";
		String info = "";
		String bucket_name = "testmyindexer";//this can be changed
		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
		S3Object o = s3.getObject(bucket_name, "storage/"+word);
		if(o == null) {
			System.out.println("Cannot find object in s3");
			return;
		}
		S3ObjectInputStream s3is = o.getObjectContent();
        InputStreamReader streamReader = new InputStreamReader(s3is);
        BufferedReader reader = new BufferedReader(streamReader);
        info = ""; 
        String line;
        if((line=reader.readLine())!=null) {
        	info = line;
        }
        System.out.println(line);
	}

}
