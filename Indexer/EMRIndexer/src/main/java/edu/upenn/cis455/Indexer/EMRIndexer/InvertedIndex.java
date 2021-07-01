package edu.upenn.cis455.Indexer.EMRIndexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
/**
 * Hadoop mapreduce for creating inverted index
 * Run on amazon EMR
 * 
 */

public class InvertedIndex {

	public static class TokenizerMapper extends Mapper<Object, Text, Text, Text>{
		private Text word = new Text();
		private Text docid = new Text();

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			int totalwords = 0;
			//get filename
			InputSplit inputSplit= (InputSplit)context.getInputSplit();
			String fileName = ((org.apache.hadoop.mapreduce.lib.input.FileSplit) inputSplit).getPath().getName();
			String html = null;
			try {
				//parse html and return the body text
				html= parseHTML(value.toString());
			} catch(Exception e) {
				e.printStackTrace();
				System.out.println("Error: "+fileName);
				return;
			}
			if(html == null) {
				System.out.println("Ignore: "+fileName);
				return;
			}
			//only store words with letters and digits 
			Pattern pattern = Pattern.compile("[\\p{Alnum}]+");
			Matcher matcher = pattern.matcher(html); 
			
			Map<String,Integer> wordmap = new LinkedHashMap<String,Integer>();
			
			PorterStemmer s = new PorterStemmer();
			while(matcher.find()) {
				String text = matcher.group().toLowerCase();
				if(!StopWords.stopwords.contains(text)) {
					totalwords++;
					//stemming words
					boolean allLetters = true;
					for(int i=0; i<text.length(); i++) {
						char ch = text.charAt(i);
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
						text = s.toString();
						s.reset();
					}
					//put word and its occurrence to word map
					if(!wordmap.containsKey(text)) {
						wordmap.put(text, 1);
					}
					else {
						int num = wordmap.get(text)+1;
						wordmap.put(text, num);
					}
				}
			}
			//calculate tf and send to reducer
			for(String text: wordmap.keySet()) {
				word.set(text);
				float tf = ((float)wordmap.get(text)/(float)totalwords);
				docid.set(fileName+" "+tf);
				context.write(word, docid);
			}
		
		}

		public String parseHTML(String html) {		
			Document d = Jsoup.parse(html);
			if(d == null) {
				return null;
			}
			String text = d.body().text();
			
			return text;
		}
	}
	public static class InvertedIndexReducer
	extends Reducer<Text,Text,Text,Text> {
		private Text result = new Text();
		public void reduce(Text key, Iterable<Text> values,
				Context context
				) throws IOException, InterruptedException {
			
			StringBuilder sb = new StringBuilder();
			for (Text val : values) {
				String text = val.toString();
				String [] pair = text.split(" ");
				String docid = pair[0];
				float tf = Float.parseFloat(pair[1]);
				
				sb.append(text+ " ");
				
			}
			
			//System.out.println(sb.toString());
			result.set(sb.toString());
			context.write(key, result);
		}
	}
	public static void main(String[] args) throws Exception {
		System.out.println("Inverted Indexer");
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "word count");
		job.setJarByClass(InvertedIndex.class);
		job.setInputFormatClass(WholeFileInputFormat.class);
		job.setMapperClass(TokenizerMapper.class);
		job.setCombinerClass(InvertedIndexReducer.class);
		job.setReducerClass(InvertedIndexReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
