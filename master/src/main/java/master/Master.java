package master;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.*;


public class Master {
	static final int NUM_OF_THREADS = 50;
	static BlockingQueue<String> frontier = new LinkedBlockingQueue<String>();
	static Set<String> crawled = ConcurrentHashMap.newKeySet();
	static Map<String, String> urlMap = new ConcurrentHashMap<>();
	
	public static void main(String[] args) {
		
		try(FileReader fr = new FileReader("crawled.txt");
			BufferedReader br = new BufferedReader(fr);) 
		{
			String line;
			while ((line = br.readLine()) != null) {
			    crawled.add(line);
			}
			    
		} catch (IOException e) {
			e.printStackTrace();
		}

		try(FileReader fr = new FileReader("urlMap.txt");
				BufferedReader br = new BufferedReader(fr);) 
			{
				String line;
				while ((line = br.readLine()) != null) {
				    String[] elems = line.split("\\|");
				    try {
						urlMap.put(elems[0], elems[1]);
					}catch( ArrayIndexOutOfBoundsException e) {
						e.getStackTrace();
					}
				}
				    
			} catch (IOException e) {
				e.printStackTrace();
			}
		
		port(8081);
		threadPool(NUM_OF_THREADS);
		
		get("/next", (req, res) -> {
			if (frontier.isEmpty()) {
				return "Empty";
			} else {
				String next = frontier.take();
				crawled.add(next);
				
				try(FileWriter fw = new FileWriter("crawled.txt", true);
					    BufferedWriter bw = new BufferedWriter(fw);
					    PrintWriter out = new PrintWriter(bw))
				{
					    out.println(next);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				return next;
			}
		});
		
		get("/put", (req, res) -> {
			String url = req.queryParams("url");
			if (url == null) {
				return "No URL";
			} else {
				if (!crawled.contains(url)) {
					frontier.put(url);
				}
				return "Success";
			}
		});
		
		get("/crawled", (req, res) -> {
			String url = req.queryParams("url");
			if (url == null) {
				return "No URL";
			} else {
				crawled.add(url);
				
				try(FileWriter fw = new FileWriter("crawled.txt", true);
					    BufferedWriter bw = new BufferedWriter(fw);
					    PrintWriter out = new PrintWriter(bw))
				{
					    out.println(url);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				return "Success";
			}
		});
		
		get("/check", (req, res) -> {
			String url = req.queryParams("url");
			if (url == null) {
				return "No URL";
			} else {
				if (crawled.contains(url)) {
					return "true";
				} else {
					return "false";
				}
			}
		});
		
		get("/out" , (req, res) -> {
			String url = req.queryParams("url");
			String dest = req.queryParams("dest");
			
			if (url == null || dest == null) {
				return "No URL";
			} else {
				if (urlMap.containsKey(url)) {
					String links = urlMap.get(url);
					links += "," + dest;
					urlMap.put(url, links);
				} else {
					urlMap.put(url, dest);
				}
				if (urlMap.size() % 100 == 0) {
					try(FileWriter fw = new FileWriter("urlMap.txt");
						    BufferedWriter bw = new BufferedWriter(fw);
						    PrintWriter out = new PrintWriter(bw))
					{
						    for (Entry<String, String> urlEntry : urlMap.entrySet()) {
						    	out.println(urlEntry.getKey() + "|" + urlEntry.getValue());
						    }
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				return "Success";
			}
		});
		
		get("/urlmap", (req, res) -> {
			
			try(FileWriter fw = new FileWriter("urlMap.txt");
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
			{
				    for (Entry<String, String> urlEntry : urlMap.entrySet()) {
				    	out.println(urlEntry.getKey() + "|" + urlEntry.getValue());
				    }
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			res.header("Content-Type", "application/download");
			res.header("Content-Disposition", "attachment; filename=urlmap.txt");
			OutputStream out = res.raw().getOutputStream();
			byte[] urlBytes = Files.readAllBytes(Paths.get("urlMap.txt"));
			out.write(urlBytes);
			out.flush();
			return res.raw();
		});

	}
}
