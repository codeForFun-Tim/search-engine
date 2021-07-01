package edu.upenn.cis455.crawler;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;

import edu.upenn.cis455.crawler.info.RobotsTxtInfo;
import edu.upenn.cis455.crawler.info.URLInfo;

public class HTTPClient {
	private Map<String, RobotsTxtInfo> rulesMap;
	private int maxFileSize;
	private InetAddress monitorHost;
	private DatagramSocket ds;
	
	// Result codes for HEAD request
	public final static String NOT_MODIFIED = "not_modified";
	public final static String REDIRECT = "redirect";
	public final static String ERROR = "error";
	public final static String UNDESIRED = "undesired";
	
	public HTTPClient(int maxFileSize, String monitorHost) {
		this.rulesMap = new HashMap<String, RobotsTxtInfo>();
		this.maxFileSize = maxFileSize;
		
		/* The first two commands need to be run only once */
		try {
			this.monitorHost = InetAddress.getByName(monitorHost);
			this.ds = new DatagramSocket();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getFileHTTP(URLInfo info) {
		try {
			Socket s = new Socket(info.getHostName(), info.getPortNo());
			
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			
			// Write the request through out
			out.write(("GET " + info.normalize() + " HTTP/1.1\r\n").getBytes());
			out.write(("Host: " + info.getHostName() + "\r\n").getBytes());
			out.write(("User-Agent: cis455crawler\r\n").getBytes());
			out.write(("Connection: close\r\n\r\n").getBytes());
			out.flush();
			// sendUDP(info);
			
			// Read initial line bytes to a string buffer
			StringBuffer buf = new StringBuffer(10000);
			int currByte;
			while (true) {
				try {
					buf.append((char) (currByte = in.read()));
					if (currByte == '\n') { // If \n, find CRLF
						break;
					}
				} catch (SocketTimeoutException e) {
					// Timeout while looking for CRLF
					s.close();
					return null;
				}
			}
			
			// Parsing the request from socket
			String currLine = buf.toString();
			
			// Obtain the status code
			String status = currLine.substring(9, 12);

			if (status.startsWith("4") || status.startsWith("5")) {
				s.close();
				return null;
			}
			
			// Then read all header bytes
			buf = new StringBuffer(10000); // reset buf
			while (true) {
				try {
					buf.append((char) (currByte = in.read()));
					if (currByte == '\n') { // If \n
						if ((currByte = in.read()) == '\r') { // Then if \r -> so we find \n\r in \r\n\r\n
							buf.append((char) currByte);
							buf.append((char) in.read());
							break;
						} else {
							buf.append((char) currByte); // Not \r, write it in and continue
						}
					}
				} catch (SocketTimeoutException e) {
					// Timeout while looking for CRLF
					s.close();
					return null;
				}
			}
			
			// Reset br to new buf
			BufferedReader br = new BufferedReader(new StringReader(buf.toString()));
			
			// Setting buf to null such that gc can collect the memory
			buf = null;
			
			// Put all headers into a map. 
			// This String is for multi-line header value
			TreeMap<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
			String prevField = "";
			while (!(currLine = br.readLine()).equals("")) { // While not reading a CRLF
				if (currLine.contains(":")) { // Normal line
					String fieldName = currLine.substring(0, currLine.indexOf(':'));
					prevField = fieldName;
					String fieldValue;
					
					// To prevent out-of-index
					if (currLine.indexOf(':') != currLine.length()-1) {
						fieldValue = currLine.substring(currLine.indexOf(':')+1).trim();
					} else {
						fieldValue = "";
					}
					
					headers.put(fieldName, fieldValue);
				} else if (!prevField.equals("") && (currLine.charAt(0) == ' ' || currLine.charAt(0) == '\t')) { 
					// A multi-line field value
					String fieldValue = headers.get(prevField);
					fieldValue += currLine;
					headers.put(prevField, fieldValue);
				}
			}
			
			if (status.startsWith("3")) {
				if (headers.containsKey("location")) {
					String newTargetStr = headers.get("location");
					try {
						newTargetStr = URLDecoder.decode(newTargetStr, StandardCharsets.UTF_8.name());
					} catch (Exception e) {
						// Do nothing
					}
					URLInfo newTarget = new URLInfo(newTargetStr);
					s.close();
					if (newTarget.getProtocol().equals("https")) {
						return getFileHTTPS(newTarget);
					} else {
						return getFileHTTP(newTarget);
					}
				} else {
					s.close();
					return null;
				}
			}
			
			// Obtain body
			if (headers.containsKey("Content-Length")) {
				int numOfBytesInBody = Integer.parseInt(headers.get("Content-Length"));
				if (numOfBytesInBody > maxFileSize) {
					s.close();
					return null;
				}
				
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
				StringBuilder sb = new StringBuilder();
				while (numOfBytesInBody > 0) {
					sb.append((char) reader.read());
					numOfBytesInBody--;
				}
				
				s.close();
				return sb.toString();
			} else {
				int numOfBytesRead = 0;
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
				StringBuilder sb = new StringBuilder();
				while (numOfBytesRead <= maxFileSize) {
					char currChar = (char) reader.read();
					if (currChar == -1) {
						break;
					}
					sb.append(currChar);
					numOfBytesRead++;
				}
				if ((int) reader.read() != -1) {
					s.close();
					return null;
				}
				
				s.close();
				return sb.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
	public String getFileHTTPS(URLInfo info) {
		try {
			URL url = new URL(info.normalize());
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.addRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", "cis455crawler");
            
            conn.connect();
            sendUDP(info);
            
            int responseCode = conn.getResponseCode();
            // if 4xx or 5xx, null
			if (responseCode >= 400) {
				return null;
			} else if (responseCode >= 300) {
				if (conn.getHeaderField("location") != null) {
					String newTargetStr = conn.getHeaderField("location");
					try {
						newTargetStr = URLDecoder.decode(newTargetStr, StandardCharsets.UTF_8.name());
					} catch (Exception e) {
						// Do nothing
					}
					URLInfo newTarget = new URLInfo(newTargetStr);
					if (newTarget.getProtocol().equals("https")) {
						return getFileHTTPS(newTarget);
					} else {
						return getFileHTTP(newTarget);
					}
				} else {
					return null;
				}
			}
			
			// Obtain body
			if (responseCode == 200 && conn.getContentLength() != -1) {
				int numOfBytesInBody = conn.getContentLength();
				if (numOfBytesInBody > maxFileSize) {
					return null;
				}
				InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
				StringBuilder sb = new StringBuilder();
				while (numOfBytesInBody > 0) {
					char nextChar = (char) reader.read();
					if (nextChar != -1) {
						sb.append(nextChar);
					}
					numOfBytesInBody--;
				}
				return sb.toString();
			} else {
				int numOfBytesRead = 0;
				InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
				StringBuilder sb = new StringBuilder();
				while (numOfBytesRead <= maxFileSize) {
					char currChar = (char) reader.read();
					if (currChar == -1) {
						break;
					}
					sb.append(currChar);
					numOfBytesRead++;
				}
				if ((int) reader.read() != -1) {
					return null;
				}
				return sb.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
	}
	
	public String sendHeadHTTP(URLInfo info, Date lastModified) {
		try {
			Socket s = new Socket(info.getHostName(), info.getPortNo());
			
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			
			// Write the request through out
			out.write(("HEAD " + info.normalize() + " HTTP/1.1\r\n").getBytes());
			out.write(("Host: " + info.getHostName() + "\r\n").getBytes());
			out.write(("User-Agent: cis455crawler\r\n").getBytes());
			if (lastModified != null) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
				dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
				out.write(("If-Modified-Since: " + dateFormat.format(lastModified) + "\r\n").getBytes());
			}
			out.write(("Connection: close\r\n\r\n").getBytes());
			out.flush();
			sendUDP(info);
			
			// Read initial line bytes to a string buffer
			StringBuffer buf = new StringBuffer(10000);
			int currByte;
			while (true) {
				try {
					buf.append((char) (currByte = in.read()));
					if (currByte == '\n') { // If \n, find CRLF
						break;
					}
				} catch (SocketTimeoutException e) {
					// Timeout while looking for CRLF
					s.close();
					return ERROR;
				}
			}
			
			// Parsing the request from socket
			String currLine = buf.toString();
			
			// Obtain the status code
			String status = currLine.substring(9, 12);

			if (status.startsWith("4") || status.startsWith("5")) {
				s.close();
				return ERROR;
			}
			
			if (status.equals("304")) {
				s.close();
				return NOT_MODIFIED;
			}
			
			// Then read all header bytes
			buf = new StringBuffer(10000); // reset buf
			while (true) {
				try {
					buf.append((char) (currByte = in.read()));
					if (currByte == '\n') { // If \n
						if ((currByte = in.read()) == '\r') { // Then if \r -> so we find \n\r in \r\n\r\n
							buf.append((char) currByte);
							buf.append((char) in.read());
							break;
						} else {
							buf.append((char) currByte); // Not \r, write it in and continue
						}
					}
				} catch (SocketTimeoutException e) {
					// Timeout while looking for CRLF
					s.close();
					return ERROR;
				}
			}
			
			// Reset br to new buf
			BufferedReader br = new BufferedReader(new StringReader(buf.toString()));
			
			// Setting buf to null such that gc can collect the memory
			buf = null;
			
			// Put all headers into a map. 
			// This String is for multi-line header value
			TreeMap<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
			String prevField = "";
			while (!(currLine = br.readLine()).equals("")) { // While not reading a CRLF
				if (currLine.contains(":")) { // Normal line
					String fieldName = currLine.substring(0, currLine.indexOf(':'));
					prevField = fieldName;
					String fieldValue;
					
					// To prevent out-of-index
					if (currLine.indexOf(':') != currLine.length()-1) {
						fieldValue = currLine.substring(currLine.indexOf(':')+1).trim();
					} else {
						fieldValue = "";
					}
					
					headers.put(fieldName, fieldValue);
				} else if (!prevField.equals("") && (currLine.charAt(0) == ' ' || currLine.charAt(0) == '\t')) { 
					// A multi-line field value
					String fieldValue = headers.get(prevField);
					fieldValue += currLine;
					headers.put(prevField, fieldValue);
				}
			}
			
			if (status.startsWith("3")) {
				if (headers.containsKey("location")) {
					String newTargetStr = headers.get("location");
					if (newTargetStr.contains(",")) {
						// Multiple choices, choose first
						newTargetStr = newTargetStr.split(",")[0].trim();
					}
					
					// Could be encoded, so try decode
					try {
						newTargetStr = URLDecoder.decode(newTargetStr, StandardCharsets.UTF_8.name());
					} catch (Exception e) {
						// Do nothing
					}
					URLInfo newTarget = new URLInfo(newTargetStr);
					
					// Add newTarget to taskQueue if it has desired URL form
					if (newTarget.isDesired()) {
						XPathCrawler.taskQueue.add(newTarget);
					}
					s.close();
					return REDIRECT;
				} else {
					s.close();
					return ERROR;
				}
			}
			
			if (status.startsWith("2")) {
				if (headers.containsKey("content-length")) {
					int targetFileSize = Integer.parseInt(headers.get("content-length").trim());
					if (targetFileSize > maxFileSize) {
						s.close();
						return UNDESIRED;
					}
				}
				
				if (headers.containsKey("content-type")) {
					String mimeType = headers.get("content-type").trim();
					if (mimeType.contains("text/html")) {
						s.close();
						return mimeType;
					} else {
						s.close();
						return UNDESIRED;
					}
				}
				
				s.close();
				return UNDESIRED;
			} else {
				s.close();
				return UNDESIRED;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			return ERROR;
		}
		
	}
	
	public String sendHeadHTTPS(URLInfo info, Date lastModified) {
		try {
			URL url = new URL(info.normalize());
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.addRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", "cis455crawler");
            if (lastModified != null) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
				dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
				conn.addRequestProperty("If-Modified-Since", dateFormat.format(lastModified));
			}
            
            conn.connect();
            sendUDP(info);
            
            int responseCode = conn.getResponseCode();
            // if 4xx or 5xx, null
			if (responseCode >= 400) {
				return ERROR;
			} else if (responseCode == 304) {
				return NOT_MODIFIED;
			} else if (responseCode >= 300) {
				if (conn.getHeaderField("location") != null) {
					String newTargetStr = conn.getHeaderField("location");
					try {
						newTargetStr = URLDecoder.decode(newTargetStr, StandardCharsets.UTF_8.name());
					} catch (Exception e) {
						// Do nothing
					}
					URLInfo newTarget = new URLInfo(newTargetStr);
					
					// Add newTarget to taskQueue if it has desired URL form
					if (newTarget.isDesired()) {
						XPathCrawler.taskQueue.add(newTarget);
					}
					
					return REDIRECT;
				} else {
					return ERROR;
				}
			}
			
			// Obtain body
			if (responseCode >= 200) {
				if (conn.getContentLength() != -1) {
					int targetFileSize = conn.getContentLength();
					if (targetFileSize > maxFileSize) {
						return UNDESIRED;
					}
				}
				
				if (conn.getContentType() != null) {
					String mimeType = conn.getContentType();
					if (mimeType.contains("text/html")) {
						return mimeType;
					} else {
						return UNDESIRED;
					}
				}
				
				return UNDESIRED;
			} else {
				return UNDESIRED;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			return ERROR;
		}
		
	}
	
	public void parseRobotsTxt(URLInfo linkInfo, String body) {
		RobotsTxtInfo robotInfo = new RobotsTxtInfo();
		
		try (BufferedReader br = new BufferedReader(new StringReader(body));) {
			String currLine;
			while ((currLine = br.readLine()) != null) {
				if (currLine.contains(":")) {
					String[] rulePair = currLine.split(":");
					if (rulePair[0].equalsIgnoreCase("User-agent")) {
						if (rulePair[1].trim().equalsIgnoreCase("cis455crawler")) {
							robotInfo.setSpecific();
							robotInfo.reset();
							currLine = br.readLine();
							while (currLine != null && !currLine.equals("")) {
								rulePair = currLine.split(":");
								if (rulePair[0].equalsIgnoreCase("Disallow")) {
									robotInfo.addDisallowedLink(rulePair[1].trim());
								} else if (rulePair[0].equalsIgnoreCase("Crawl-Delay")) {
									robotInfo.addCrawlDelay(Integer.parseInt(rulePair[1].trim()) * 1000);
								}
								currLine = br.readLine();
							}
						} else if (rulePair[1].trim().equalsIgnoreCase("*") && !robotInfo.isSpecific()) {
							currLine = br.readLine();
							while (currLine != null && !currLine.equals("")) {
								rulePair = currLine.split(":");
								if (rulePair[0].equalsIgnoreCase("Disallow")) {
									robotInfo.addDisallowedLink(rulePair[1].trim());
								} else if (rulePair[0].equalsIgnoreCase("Crawl-Delay")) {
									robotInfo.addCrawlDelay(Integer.parseInt(rulePair[1].trim()) * 1000);
								}
								currLine = br.readLine();
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		rulesMap.put(linkInfo.getHostName() + ":" + linkInfo.getPortNo(), robotInfo);
	}
	
	public Map<String, RobotsTxtInfo> getRulesMap() {
		return this.rulesMap;
	}
	
	private void sendUDP(URLInfo task) {
		/* The commands below need to be run for every single URL */
		byte[] data = ("haohua;" + task.normalize()).getBytes();
		DatagramPacket packet = new DatagramPacket(data, data.length, this.monitorHost, 10455);
		try {
			this.ds.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
