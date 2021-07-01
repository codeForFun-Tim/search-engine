package edu.upenn.cis455.storage;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 * Custom class for channel info
 *
 */
public class ChannelInfo implements Serializable {

	private static final long serialVersionUID = 2550660622747758407L;
	private String author;
	private String pattern;
	private List<String> docs = new LinkedList<String>();
	
	public ChannelInfo(String author, String pattern) {
		this.author = author;
		this.pattern = pattern;
	}

	public String getAuthor() {
		return author;
	}

	public String getPattern() {
		return pattern;
	}
	
	public void reset() {
		this.docs = new LinkedList<String>();
	}
	
	public void addLink(String normalizedLink) {
		this.docs.add(normalizedLink);
	}
	
	public List<String> getDocList() {
		return this.docs;
	}
	
}
