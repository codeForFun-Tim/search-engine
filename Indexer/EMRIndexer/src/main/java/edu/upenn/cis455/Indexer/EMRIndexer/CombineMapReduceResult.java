package edu.upenn.cis455.Indexer.EMRIndexer;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
/**
 * Some new pages are crawled after running indexer. So EMR may run several times.
 * Combine results from different EMR result. 
 * Run on amazon EMR. Put all input files into one folder before running.
 * 
 */

public class CombineMapReduceResult {

	public static class TokenizerMapper extends Mapper<Object, Text, Text, Text>{
		private Text word = new Text();
		private Text info = new Text();

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
				String[] kv = value.toString().split("\\s+",2);
				word.set(kv[0]);
				info.set(kv[1]);
				context.write(word, info);
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
				sb.append(text+ " ");

			}

			//System.out.println(sb.toString());
			result.set(sb.toString());
			context.write(key, result);
		}
	}
	public static void main(String[] args) throws Exception {
		System.out.println("Combine result");
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "Combine result");
		job.setJarByClass(InvertedIndex.class);
		job.setInputFormatClass(TextInputFormat.class);
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
