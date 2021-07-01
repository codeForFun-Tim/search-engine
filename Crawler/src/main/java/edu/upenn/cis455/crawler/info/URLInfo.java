package edu.upenn.cis455.crawler.info;

/** (MS1, MS2) Holds information about a URL.
  */
public class URLInfo {
	private String protocol;
	private String hostName;
	private int portNo;
	private String filePath;
	
	/**
	 * Constructor called with raw URL as input - parses URL to obtain host name and file path
	 */
	public URLInfo(String docURL){
		if(docURL == null || docURL.equals("")) {
			return;
		}
		docURL = docURL.trim();
		if((!docURL.startsWith("http://") && !docURL.startsWith("https://")) || docURL.length() < 8) {
			return;
		}
		if (docURL.startsWith("http://")) {
			protocol = "http";
			// Stripping off 'http://'
			docURL = docURL.substring(7);
		} else if (docURL.startsWith("https://")) {
			protocol = "https";
			// Stripping off 'https://'
			docURL = docURL.substring(8);
		}
		
		/*If starting with 'www.' , stripping that off too
		if(docURL.startsWith("www."))
			docURL = docURL.substring(4);*/
		int i = 0;
		while(i < docURL.length()){
			char c = docURL.charAt(i);
			if(c == '/')
				break;
			i++;
		}
		String address = docURL.substring(0,i);
		if(i == docURL.length())
			filePath = "/";
		else
			filePath = docURL.substring(i); //starts with '/'
		if(address.equals("/") || address.equals("")) {
			return;
		}
		if(address.indexOf(':') != -1){
			String[] comp = address.split(":",2);
			hostName = comp[0].trim();
			try{
				portNo = Integer.parseInt(comp[1].trim());
			}catch(NumberFormatException nfe){
				if (protocol.equals("http")) {
					portNo = 80;
				} else {
					portNo = 443;
				}
			}
		}else{
			hostName = address;
			if (protocol.equals("http")) {
				portNo = 80;
			} else {
				portNo = 443;
			}
		}
	}
	
	public URLInfo(String protocol, String hostName, String filePath){
		this.protocol = protocol;
		this.hostName = hostName;
		this.filePath = filePath;
		if (protocol.equals("http")) {
			this.portNo = 80;
		} else {
			this.portNo = 443;
		}
	}
	
	public URLInfo(String hostName, String filePath){
		this.protocol = "http";
		this.hostName = hostName;
		this.filePath = filePath;
		this.portNo = 80;
	}
	
	public URLInfo(String protocol, String hostName, int portNo, String filePath){
		this.protocol = protocol;
		this.hostName = hostName;
		this.portNo = portNo;
		this.filePath = filePath;
	}
	
	public String getHostName(){
		return hostName;
	}
	
	public void setHostName(String s){
		hostName = s;
	}
	
	public int getPortNo(){
		return portNo;
	}
	
	public void setPortNo(int p){
		portNo = p;
	}
	
	public String getFilePath(){
		return filePath;
	}
	
	public void setFilePath(String fp){
		filePath = fp;
	}
	
	public String getProtocol() {
		return protocol;
	}
	
	public String normalize() {
		return protocol + "://" + hostName + ":" + portNo + filePath;
	}
	
	public boolean isDesired() {
		if (filePath == null) {
			return false;
		}
		if (!filePath.contains(".")) {
			return true;
		} else if (filePath.endsWith(".htm") || filePath.endsWith(".html") || filePath.endsWith(".xml") || filePath.endsWith(".rss")) {
			return true;
		}
		return false;
	}
	
}
