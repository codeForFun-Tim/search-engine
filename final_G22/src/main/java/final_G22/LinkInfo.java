package final_G22;

public class LinkInfo {
	String link;
	double tfidf;
	double pageRank;
	String title;
	String body;
	double harmonic;
	
	public LinkInfo(String link) {
		this.link = link;
	}
	
	public LinkInfo(String link, double tfidf) {
		this.link = link;
		this.tfidf = tfidf;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getTfidf() {
		return Double.toString(tfidf);
	}

	public void setTfidf(double tfidf) {
		this.tfidf = tfidf;
	}

	public double getPageRank() {
		return pageRank;
	}

	public void setPageRank(double pageRank) {
		this.pageRank = pageRank;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public double getHarmonic() {
		return harmonic;
	}

	public void setHarmonic(double harmonic) {
		this.harmonic = harmonic;
	}
	
	
}
