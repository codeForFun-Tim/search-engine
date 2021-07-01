package edu.upenn.cis455.frontend;

import static spark.Spark.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import edu.upenn.cis455.crawler.info.URLInfo;
import edu.upenn.cis455.storage.ChannelInfo;
import edu.upenn.cis455.storage.DBWrapper;
import edu.upenn.cis455.storage.DocInfo;
import edu.upenn.cis455.storage.UserInfo;

class XPathApp {
	public static void main(String args[]) {

    if (args.length != 1) {
      System.err.println("You need to provide the path to the BerkeleyDB data store!");
      System.exit(1);
    }
    
    // Initiate the DB
    DBWrapper myDB;
    
    try {
    	myDB = DBWrapper.getInstance(args[0]);
    	
    } catch (Exception e) {
    	e.printStackTrace();
    	return;
    }
  
    port(8080);

    /* Displays a login form if the user is not logged in yet (i.e., the "username" attribute
       in the session has not been set yet), and welcomes the user otherwise */

    get("/", (request, response) -> {
    	String username = (String) (request.session().attribute("user"));
        String firstName = (String) (request.session().attribute("firstname"));
        String lastName = (String) (request.session().attribute("lastname"));
        if (username == null) {
        	return "<html><body>Please enter your username and password: <form action=\"/login\" method=\"POST\"><label for=\"uname\">Username:</label><br><input type=\"text\" id=\"uname\" name=\"username\"/><br><label for=\"pwd\">Password:</label><br><input type=\"text\" id=\"pwd\" name=\"password\"/><br><input type=\"submit\" value=\"Log in\"/></form><p><a href=\"/newaccount\">Create a New Account</a></body></html>";
        } else {
        	String res = "<html><body>Welcome, " + firstName + " " + lastName + "!<br>Crawled urls:<br>";
        	List<String> channels = myDB.getURLs();
        	UserInfo info = myDB.getUserInfo(username);
        	
        	for (String channel : channels) {
        		if (info.ifSubscribed(channel)) {
        			res += "<a href=\"/show?name=" + channel + "\">" + channel + "</a>";
        			DocInfo docInfo = myDB.getDocInfo(channel);
//        			if (docInfo.getAuthor().equals(username)) {
//        				res += "&#9<a href=\"/delete?name=" + channel + "\">delete</a><br>";
//        			} else {
//        				res += "<br>";
//        			}
        			res += "<br>";
        		} else {
        			res += channel + "&#9<a href=\"/subscribe?name=" + channel + "\">subscribe</a><br>";
        		}
        	}
        	
        	res += "<br><form action=\"/create\" method=\"GET\"><label for=\"name\">New Channel Name:</label><br><input type=\"text\" id=\"name\" name=\"name\"/><br><label for=\"path\">XPath pattern:</label><br><input type=\"text\" id=\"path\" name=\"xpath\"/><br><input type=\"submit\" value=\"Create New Channel\"/></form></body></html>";
        	return res;
        }
    });
    
    /* Displays a register form and post the form to /register when submitted */
    
    get("/newaccount", (request, response) -> {
    	return "<html><body>Please fill in the info: <form action=\"/register\" method=\"POST\"><label for=\"uname\">Username:</label><br><input type=\"text\" id=\"uname\" name=\"username\"/><br><label for=\"fname\">First name:</label><br><input type=\"text\" id=\"fname\" name=\"firstname\"/><br><label for=\"lname\">Last name:</label><br><input type=\"text\" id=\"lname\" name=\"lastname\"/><br><label for=\"pwd\">Password:</label><br><input type=\"text\" id=\"pwd\" name=\"password\"/><br><input type=\"submit\" value=\"Register\"/></form></body></html>";
    });

    /* Receives the data from the login form, logs the user in, and redirects the user back to /. */

    post("/login", (request, response) -> {
    	String username = request.queryParams("username");
        String rawPwd = request.queryParams("password");
        
        if (username != null && rawPwd != null) {
        	UserInfo info = null;
        	try {
        		info = myDB.getUserInfo(username);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        	if (info == null) {
        		// Account not exist, send an error message
        		return "<html><body>Username is not registered!<p><a href=\"/\">Try again</a></body></html>";
        	}
        	
        	MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] encodedPwd = md.digest(rawPwd.getBytes(StandardCharsets.UTF_8));
        	if (!info.checkPwdEqual(encodedPwd)) {
        		// Wrong password
        		return "<html><body>Password is incorrect!<p><a href=\"/\">Try again</a></body></html>";
        	}
        	
        	// Success match
        	request.session().attribute("user", username);
        	request.session().attribute("firstname", info.getFirstName());
        	request.session().attribute("lastname", info.getLastName());
        	response.redirect("/");
            return null;
        } else {
        	// Send an error page
        	return "<html><body>Empty username or password!<p><a href=\"/\">Try again</a></body></html>";
        }
    });

    /* Receives the data from the register form and write the data into the BDB. */
    
    post("/register", (request, response) -> {
        String username = request.queryParams("username");
        String rawPwd = request.queryParams("password");
        String firstName = request.queryParams("firstname");
        String lastName = request.queryParams("lastname");
        
        if (username != null && rawPwd != null && firstName != null && lastName != null) {
        	UserInfo info = null;
        	try {
        		info = myDB.getUserInfo(username);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        	if (info != null) {
        		// Already exist, send an error message
        		return "<html><body>Username already in use!<p><a href=\"/newaccount\">Try again</a></body></html>";
        	}
        	
        	MessageDigest md = MessageDigest.getInstance("SHA-256");
        	byte[] encodedPwd = md.digest(rawPwd.getBytes(StandardCharsets.UTF_8));
        	info = new UserInfo(firstName, lastName, encodedPwd);
        	
        	try {
        		myDB.addUserInfo(username, info);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        	
        	// If success
        	request.session().attribute("user", username);
        	request.session().attribute("firstname", firstName);
        	request.session().attribute("lastname", lastName);
        	response.redirect("/");
            return null;
        } else {
        	// Send an error page
        	return "<html><body>Please fill in all information!<p><a href=\"/newaccount\">Try again</a></body></html>";
        }
        
      });
    
    /* Logs the user out by deleting the "username" attribute from the session. You could also
       invalidate the session here to get rid of the JSESSIONID cookie entirely. */

    get("/logout", (request, response) -> {
    	request.session().invalidate();
    	response.redirect("/");
    	return null;
    });
    
    /* Look up a document in store*/
    get("/lookup", (request, response) -> {
        String URL = request.queryParams("url");
        
        if (URL == null) {
        	// Send an error page
        	return "<html><body>Please include an URL!</body></html>";
        }
        
        // Decode and normalize
        try {
        	URL = URLDecoder.decode(URL, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
        	// Do nothing
        }
        
        URL = new URLInfo(URL).normalize();
        
        DocInfo info = null;
        try {
        	info = myDB.getDocInfo(URL);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        
        if (info != null) {
        	if (info.getMimeType() != null) {
        		response.type(info.getMimeType());
        	}
			return info.getContent();
        	
        } else {
        	// Send 404
        	response.status(404);
        	return null;
        }
    });
    
    /* Create a channel */
    get("/create", (request, response) -> {
    	String user = (String) request.session().attribute("user");
        String name = request.queryParams("name");
        String pattern = request.queryParams("xpath");
        
        // Check for log in
        if (user == null) {
        	response.status(401);
        	return "<html><body>Please log in first!</body></html>";
        }
        
        // Check for channel name/pattern
        if (name == null || pattern == null) {
        	response.status(409);
        	return "<html><body>Please provide the name and/or the xpath pattern!</body></html>";
        }
        
        // Check if exists
        ChannelInfo info = null;
    	try {
    		info = myDB.getChannelInfo(name);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	if (info != null) {
    		// Already exist, send an error message
    		response.status(409);
    		return "<html><body>Channel already exists!</body></html>";
    	}
    	
    	info = new ChannelInfo(user, pattern);
    	
    	try {
    		myDB.addChannelInfo(name, info);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
        
    	// Success
    	return "<html><body>You have created channel " + name + "!<p><a href=\"/\">Back to Home Page</a></body></html>";
    });
    
    /* Delete a channel */
    get("/delete", (request, response) -> {
    	String user = (String) request.session().attribute("user");
        String name = request.queryParams("name");
        
        // Check for log in
        if (user == null) {
        	response.status(401);
        	return "<html><body>Please log in first!</body></html>";
        }
        
        // Check for channel name
        if (name == null) {
        	response.status(409);
        	return "<html><body>Please provide the channel name!</body></html>";
        }
        
        // Check if exists
        ChannelInfo info = null;
    	try {
    		info = myDB.getChannelInfo(name);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	if (info == null) {
    		// Missing
    		response.status(404);
    		return "<html><body>Channel is not found!</body></html>";
    	}
    	
    	if (!info.getAuthor().equals(user)) {
    		response.status(403);
    		return "<html><body>You are not the creator of the channel!</body></html>";
    	}
        
    	try {
    		myDB.deleteChannelInfo(name);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	// Success
    	return "<html><body>You have deleted channel " + name + "!<p><a href=\"/\">Back to Home Page</a></body></html>";
    });
    
    /* Subscribe a channel */
    get("/subscribe", (request, response) -> {
    	String user = (String) request.session().attribute("user");
        String name = request.queryParams("name");
        
        // Check for log in
        if (user == null) {
        	response.status(401);
        	return "<html><body>Please log in first!</body></html>";
        }
        
        // Check for channel name
        if (name == null) {
        	response.status(409);
        	return "<html><body>Please provide the channel name!</body></html>";
        }
        
        // Check if already subscribed
        UserInfo userInfo = null;
        try {
        	userInfo = myDB.getUserInfo(user);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        if (userInfo == null) {
        	// This would be weird since logged in
        	response.status(500);
        	return null;
        }
        if (userInfo.ifSubscribed(name)) {
        	response.status(409);
        	return "<html><body>You already subscribed!</body></html>";
        }
        
        // Check if exists
        ChannelInfo info = null;
    	try {
    		info = myDB.getChannelInfo(name);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	if (info == null) {
    		// Missing
    		response.status(404);
    		return "<html><body>Channel is not found!</body></html>";
    	}
    	
        // Success. Add to list, and store to db
    	userInfo.subscribe(name);
    	try {
    		myDB.addUserInfo(user, userInfo);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return "<html><body>You have subscribed channel " + name + "!<p><a href=\"/\">Back to Home Page</a></body></html>";
    });
    
    /* Unsubscribe a channel */
    get("/unsubscribe", (request, response) -> {
    	String user = (String) request.session().attribute("user");
        String name = request.queryParams("name");
        
        // Check for log in
        if (user == null) {
        	response.status(401);
        	return "<html><body>Please log in first!</body></html>";
        }
        
        // Check for channel name
        if (name == null) {
        	response.status(409);
        	return "<html><body>Please provide the channel name!</body></html>";
        }
        
        // Check if subscribed
        UserInfo userInfo = null;
        try {
        	userInfo = myDB.getUserInfo(user);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        if (userInfo == null) {
        	// This would be weird since logged in
        	response.status(500);
        	return null;
        }
        if (!userInfo.ifSubscribed(name)) {
        	response.status(404);
        	return "<html><body>You didn't subscribe this channel!</body></html>";
        }
    	
        // Success. Add to list, and store to db
    	userInfo.unsubscribe(name);
    	try {
    		myDB.addUserInfo(user, userInfo);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return "<html><body>You have unsubscribed channel " + name + "!<p><a href=\"/\">Back to Home Page</a></body></html>";
    });
    
    /* Show a channel */
    get("/show", (request, response) -> {
    	String user = (String) request.session().attribute("user");
        String name = request.queryParams("name");
        
        // Check for log in
        if (user == null) {
        	response.status(401);
        	return "<html><body>Please log in first!</body></html>";
        }
        
        // Check for channel name
        if (name == null) {
        	response.status(409);
        	return "<html><body>Please provide the channel name!</body></html>";
        }
        
        // Check if already subscribed
        UserInfo userInfo = null;
        try {
        	userInfo = myDB.getUserInfo(user);
        } catch (Exception e) {
        	e.printStackTrace();
        }
        if (userInfo == null) {
        	// This would be weird since logged in
        	response.status(500);
        	return null;
        }
        if (!userInfo.ifSubscribed(name)) {
        	response.status(409);
        	return "<html><body>You didn't subscribe this channel!</body></html>";
        }
        
        // Check if exists
        ChannelInfo info = null;
    	try {
    		info = myDB.getChannelInfo(name);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	if (info == null) {
    		// Missing
    		response.status(404);
    		return "<html><body>Channel is not found!</body></html>";
    	}
    	
        // Success. Show the list
    	String res = "<!DOCTYPE html><html><body><div class=\"channelheader\">";
    	res += "Channel name: " + name + ", created by: " + userInfo.getFirstName() + " " + userInfo.getLastName() + "</div>";
    	
    	SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-DD'T'hh:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	for (String docLink : info.getDocList()) {
    		DocInfo docInfo = null;
            try {
            	docInfo = myDB.getDocInfo(docLink);
            } catch (Exception e) {
            	e.printStackTrace();
            }
            if (docInfo != null) {
            	res += "<br>Crawled on " + dateFormat.format(docInfo.getLastModified());
            	res += "<br>Location: " + docInfo.getNormalizedLink();
            	res += "<div class=”document”><xmp>" + docInfo.getContent().toString() + "</xmp></div>"; 
            }
    	}
    	res += "</body></html>";
    	return res;
    });
    
  }
}