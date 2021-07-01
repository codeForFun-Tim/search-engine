package edu.upenn.cis455.storage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

/**
 * 
 * Custom class for extracted doc info
 *
 */
public class DocInfo implements Serializable {

	private static final long serialVersionUID = 4934004289851524630L;
	private String normalizedLink;
	private Date lastModified;
	private String mimeType;
	private String content;
	
	public DocInfo(String l, String m, String c) {
		this.normalizedLink = l;
		this.lastModified = new Date(System.currentTimeMillis());
		this.mimeType = m;
		this.content = c;
	}

	public Date getLastModified() {
		return lastModified;
	}

	public void setLastModifiedToCurrent() {
		this.lastModified = new Date(System.currentTimeMillis());
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public String getNormalizedLink() {
		return this.normalizedLink;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Objects.hash(content);
		result = prime * result + Objects.hash(lastModified, mimeType, normalizedLink);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof DocInfo))
			return false;
		DocInfo other = (DocInfo) obj;
		return Objects.equals(content, other.content) && Objects.equals(lastModified, other.lastModified)
				&& Objects.equals(mimeType, other.mimeType) && Objects.equals(normalizedLink, other.normalizedLink);
	}
	
}
