package edu.upenn.cis455.storage;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class S3Wrapper {
	
	
	public static void uploadToS3(DocInfo doc) {
		AWSCredentials credentials = new BasicSessionCredentials(
	    		  "ASIAXKWC5YV3BKY2OTEA", 
	    		  "VguvyPMKTLOm15tk4GhagqXaL8mahmhUeiyUrWhK",
				  "FwoGZXIvYXdzELr//////////wEaDOVtoC8WVPQXFDRh5iLEAQdSx/90iaIMLXaWjcfdtRjVZzALAUJOh5HwIEL2W07FMurl1Bg4LQmZxnmXGiBKQy5pgdV5AFx4finYaI54SrK57O2bupLfRi9SQJW5p9xTT/BXEDO+a5zRkoXkbTE4I3ZPpzcWTTy98eKMJU3/UK5M5HMq/nJQuA74K25RKOK2lU8YXBlk8qldhcVDv51Ulnyzj4QsSA+kpyZBLRbsxU876ThZ8R8Lq2ngz5NwZg8LFYwso/5m+kZgqCzmWPj+pYr95foo3//X9QUyLZChh7rttksSQG4x1DHnD5T/ofPwoGl7WxCRdYbK+WeQ7v2rY2qP5s20bjDoxw=="
				  );
		AmazonS3 s3 = AmazonS3ClientBuilder.standard()
					.withCredentials(new AWSStaticCredentialsProvider(credentials))
					.withRegion(Regions.US_EAST_1)
					.build();
		
	    try{
	    	String cleaned = doc.getContent();
	    	cleaned = cleaned.replaceAll("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cn}]", "");
	    	byte[] bytes = cleaned.getBytes(StandardCharsets.UTF_8);
			ByteArrayInputStream out = new ByteArrayInputStream(bytes);
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(bytes.length);
			meta.setContentType(doc.getMimeType());
			meta.setLastModified(doc.getLastModified());
			
			String key = "final/" + URLEncoder.encode(doc.getNormalizedLink(), "UTF-8");
			
			s3.putObject(new PutObjectRequest("testmyindexer", key, out, meta));
			
			
		} catch (Exception e) {
            e.printStackTrace();
        } 

	}

}
