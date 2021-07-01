package edu.upenn.cis455.project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;

import com.google.common.collect.Iterables;

import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

public class PagerankMain {
	
	private static final Pattern SPACES = Pattern.compile("\\s+");
	
	public static void main(String args[]) 
	{
		
		if (args.length < 2) {
            System.err.println("Usage: PagerankMain <inputFile> <outputFile>");
            System.exit(1);
        }

        SparkSession spark = SparkSession
                .builder()
                .appName("SparkJob")
                .getOrCreate();

        // read text file to RDD
        JavaRDD<String> lines = spark.read().textFile(args[0]).toJavaRDD();
//        JavaRDD<String> lines = spark.read().textFile("links.txt").toJavaRDD();
		
//		System.out.println("*** Author: Sanjna Kashyap (sanjnak)");
//		SparkConf sparkConf = new SparkConf().setAppName("Pagerank")
//                .setMaster("local[2]").set("spark.executor.memory","2g");
//		// start a spark context
//		JavaSparkContext sc = new JavaSparkContext(sparkConf);
		
		
		// provide path to input text file
//		String path = "links.txt";
		
		
//		JavaRDD<String> lines = sc.textFile(path);
		
        JavaPairRDD<String, String> urlmap = lines.mapToPair((String s) -> {
//		      String[] parts = SPACES.split(s);
			  String[] parts = s.split("\\|");
//			  System.out.println(parts[0]+"PARTS"+parts[1]);
			  if(parts.length<2)
			  {
				  return new Tuple2<>(parts[0], "");
			  }
			  else
			  {
				  return new Tuple2<>(parts[0], parts[1]);
			  }
		      
		    });
//		System.out.println(urlmap.collect());
		
		JavaPairRDD<String, String> oldlinks = urlmap.flatMapToPair(s -> {
			String url = s._1();
			String dest = s._2();
			String[] parts = dest.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
			List<Tuple2<String, String>> results = new ArrayList<>();
	          for (String n : parts) {
	            results.add(new Tuple2<>(url, n));
//	            System.out.println(urlCount+n);
	          }
	          return results.iterator();
		});
//		System.out.println(oldlinks.collect());
		
		JavaPairRDD<String, Iterable<String>> links = oldlinks.distinct().groupByKey().cache();
		System.out.println(links.count());
//		Double initial = (double) (1/links.count());
		Double totalsum = (double) links.count();
//		System.out.println(links.collect());
//		JavaPairRDD<String, Double> ranks = links.mapValues(rs -> initial);
		JavaPairRDD<String, Double> ranks = links.mapValues(rs -> (double) 1);
//		System.out.println(ranks.collect());
		
		for (int current = 0; current < 30; current++) {
		      // Calculates URL contributions to the rank of other URLs.
//		      JavaPairRDD<String, Double> contribs = links.join(ranks).values()
			JavaPairRDD<String, Double> contribs = links.join(ranks)
		        .flatMapToPair(s -> {
		          String url = s._1();
		          Tuple2<Iterable<String>, Double> t = s._2();
		          int urlCount = Iterables.size(t._1());
//		          System.out.println(urlCount);
		          List<Tuple2<String, Double>> results = new ArrayList<>();
		          for (String n : t._1) {
		            results.add(new Tuple2<>(n, t._2() / urlCount));
//		            System.out.println(urlCount+n);
		          }
		          results.add(new Tuple2<>(url, (double) 0));
		          return results.iterator();
		        });
//		      System.out.println(contribs.collect());
		      ranks = contribs.reduceByKey((a, b) -> a+b).mapValues(sum -> 0.15 + sum * 0.85);
//		      System.out.println(ranks.collect());
		      
		      // Dealing with dangling links i.e. removing ranks of sinks and dangling links
		      ranks = links.join(ranks).mapToPair(f -> {
		    	  String url = f._1();
		    	  Tuple2<Iterable<String>, Double> d = f._2();
		    	  return new Tuple2<>(url, d._2());
		      });
		      
		      // Normalizing pagerank - all values sum up to 1
		      Double v = ranks.values().reduce((a, b) -> a+b);
//		      System.out.println(v);
		      ranks = ranks.mapToPair(x -> {
		    	  Double t = (x._2()/v) * totalsum; 
		    	  return new Tuple2<>(x._1(), t);
		    	  });
	    }
		
		// flatMap each line to words in the line
//		JavaRDD<String> words = lines.flatMap(s -> Arrays.asList(s.split(" ")).iterator());
//		JavaRDD<> words = lines.maptoPair(s -> { Tuple2<String, String> first = s.split(" ");});
		

		// collect RDD for printing
		ranks.saveAsTextFile(args[1]);
//		for(String word:){
//		System.out.println(word);
//		}
//		sc.close();
	}
}
