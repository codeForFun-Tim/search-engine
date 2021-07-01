package edu.upenn.cis455.xpathengine;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.w3c.dom.Document;
import org.xml.sax.helpers.DefaultHandler;

/** (MS2) Implements XPathEngine to handle XPaths.
  */
public class XPathEngineImpl implements XPathEngine {
	
	


  public XPathEngineImpl() {
    // Do NOT add arguments to the constructor!!
  }
	
  public void setXPaths(String[] s) {
    /* TODO: Store the XPath expressions that are given to this method */
  }

  public boolean isValid(int i) {
    /* TODO: Check which of the XPath expressions are valid */
    return false;
  }
	
  public boolean[] evaluate(Document d) { 
    /* TODO: Check whether the document matches the XPath expressions */
    return null; 
  }

  @Override
  public boolean isSAX() {
	  return false;
  }

  @Override
  public boolean[] evaluateSAX(InputStream document, DefaultHandler handler) {
	  return null;
  }
  
  public static String strip(String xpath) {
	  String res = "";
	  boolean inQuote = false;
	  
	  for (int i = 0; i < xpath.length(); i++) {
		  char curr = xpath.charAt(i);
		  
		  if (curr == '"' && inQuote == false) {
			  inQuote = true;
			  res += curr;
		  } else if (curr == '"' && inQuote == true) {
			  inQuote = false;
			  res += curr;
		  } else if (curr == ' ' && inQuote == false) {
			  // Do nothing
		  } else {
			  res += curr;
		  }
	  }
	  
	  return res;
  }
  
  public static void tokenize(String p, Queue<String> queue) {
	  if (p.length() == 0) {
		  // finished
		  return;
	  }
	  
	  if (p.charAt(0) == '/' || p.charAt(0) == '[' || p.charAt(0) == ']') {
		  queue.add(p.substring(0,1));
		  tokenize(p.substring(1), queue);
		  return;
	  }
	  
	  String currToken = "";
	  int special = -1;
	  for (int i = 0; i < p.length(); i++) {
		  if (p.charAt(i) == '/' || p.charAt(i) == '[' || p.charAt(i) == ']') {
			  special = i;
			  break;
		  }
		  currToken += p.charAt(i);
	  }
	  queue.add(currToken);
	  if (special != -1) {
		  tokenize(p.substring(special), queue);
		  return;
	  }
  }
  
  public static boolean isValidAxis(Queue<String> path, int count, int depth, List<List<String>> rule) {
	  if (path.isEmpty()) {
		  return false;
	  }
	  
	  if (path.poll().equals("/")) {
		  return isValidStep(path, count, depth, rule);
	  }
	  return false;
  }
        
  public static boolean isValidStep(Queue<String> path, int count, int depth, List<List<String>> rule) {
	  if (path.isEmpty()) {
		  return false;
	  }
	  String next = path.poll();
	  // Node name?
	  if (next.matches("[A-Za-z0-9]+")) {
		  if (path.isEmpty()) {
			  if (count == 0) {
				  rule.get(depth).add(next);
				  return true;
			  } else {
				  return false;
			  }
		  }
		  next = path.peek();
		  if (next.equals("/")) {
			  return isValidAxis(path, count, depth+1, rule);
		  } else if (next.equals("[")) {
			  path.poll();
			  return isValidTest(path, count+1, depth+1, rule);
		  } else {
			  return false;
		  }
		  
	  } else {
		  return false;
	  }
  }
  
  public static boolean isValidTest(Queue<String> path, int count, int depth, List<List<String>> rule) {
	  String next = path.peek();
	  if (next.matches("text\\(\\)=\"[^\"]*\"") || next.matches("contains\\(text\\(\\),\"[^\"]*\"\\)") || next.matches("@[A-Za-z0-9]+=\"[^\"]*\"") ) {
		  rule.get(depth).add(next);
		  path.poll();
		  if (!path.poll().equals("]")) {
			  return false;
		  } else {
			  count--;
		  }
		  
		  if (path.isEmpty()) {
			  if (count == 0) {
				  return true;
			  } else {
				  return false;
			  }
		  }
		  next = path.peek();
		  if (next.equals("/")) {
			  return isValidAxis(path, count, depth+1, rule);
		  } else if (next.equals("[")) {
			  path.poll();
			  return isValidTest(path, count+1, depth, rule);
		  } else {
			  return false;
		  }
	  } else if (next.matches("[A-Za-z0-9]+")) {
		  return false;
	  }
	  return false;
	  
	  
  }
}
