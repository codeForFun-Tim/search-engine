package edu.upenn.cis455.storage;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

/** (MS1, MS2) A wrapper class which should include:
  * - Set up of Berkeley DB
  * - Saving and retrieving objects including crawled docs and user information
  */
public class DBWrapper {
	private static DBWrapper instance;
	
	private static String envDirectory = null;
	
	private static Environment myEnv;
	//private static EntityStore store;
	
	private static final String USER_STORE = "user_store";
	private static final String DOC_STORE = "doc_store";
	private static final String CHANNEL_STORE = "channel_store";
	private static final String CLASS_CATALOG = "class_catalog";
	
	private StoredClassCatalog catalog = null;
	private Database userDB = null;
	private Database docDB = null;
	private Database channelDB = null;
	private Database catalogDB = null;

	// Instantiate the bindings for the key and value classes
	private EntryBinding userInfoBinding;
	private EntryBinding docInfoBinding;
	private EntryBinding channelInfoBinding;
	
	public static DBWrapper getInstance(String dir) {
		if (instance == null) {
			instance = new DBWrapper(dir);
		}
		return instance;
	}
	
	private DBWrapper(String dir)
	{
		envDirectory = dir;
		
		try {
			// Create EnvironmentConfig object
			EnvironmentConfig envConfig = new EnvironmentConfig();
			
			// Environment should be capable of performing transactions
			envConfig.setTransactional(true);
			
			// Create a database environment if it doesn’t already exist
			envConfig.setAllowCreate(true);
			
			// Instantiate environment
			myEnv = new Environment(new File(envDirectory), envConfig);
			
			// Create DatabaseConfig object
			DatabaseConfig dbConfig = new DatabaseConfig();
			
			// Encloses the database open within a transaction.
			dbConfig.setTransactional(true);
			
			// Create the database if it does not already exist
			dbConfig.setAllowCreate(true);
			
			// Instantiate a catalog database to keep track of the database’s metadata
			this.catalogDB = myEnv.openDatabase(null, CLASS_CATALOG, dbConfig);
			this.catalog = new StoredClassCatalog(catalogDB);
			
			// Instantiate user database
			this.userDB = myEnv.openDatabase(null,USER_STORE, dbConfig);
			this.docDB = myEnv.openDatabase(null, DOC_STORE, dbConfig);
			this.channelDB = myEnv.openDatabase(null, CHANNEL_STORE, dbConfig);
			
			// Instantiate the bindings for the key and value classes
			this.userInfoBinding = new SerialBinding(this.catalog, UserInfo.class);
			this.docInfoBinding = new SerialBinding(this.catalog, DocInfo.class);
			this.channelInfoBinding = new SerialBinding(this.catalog, ChannelInfo.class);
			
		} catch(DatabaseException e) {
			throw e;
		}
	}
	
	// Return Environment instance
	public Environment getEnv()
	{
		return myEnv;
	}
	
	// Return userDB instance
	public Database getUserDB()
	{
		return this.userDB;
	}
	
	// Return docDB instance
	public Database getDocDB()
	{
		return this.docDB;
	}
	
	public Database getChannelDB() {
		return this.channelDB;
	}
	
	// Return userDB val binding
	public EntryBinding getUserInfoBinding()
	{
		return this.userInfoBinding;
	}
	
	// Return docDB val binding
	public EntryBinding getDocInfoBinding()
	{
		return this.docInfoBinding;
	}
	
	public EntryBinding getChannelInfoBinding() {
		return this.channelInfoBinding;
	}
	
	// Method for registering a new account - putting info to the DB
	public void addUserInfo(String username, UserInfo info)
	{
		// Declare the database entry key-value pair that need to be stored in userDB
		DatabaseEntry theKey = new DatabaseEntry(username.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();
		
		// Associate the bindings with the database entries
		this.getUserInfoBinding().objectToEntry(info, theVal);

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		
		try {
			// Insert key-value pair in userDB
			this.getUserDB().put(txn, theKey, theVal);
			
			// Commit the transaction
			txn.commit();
			
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
	}
	
	// Method for validating a log in - getting info from the DB
	public UserInfo getUserInfo(String username)
	{
		// Declare the database entry key-value pair that need to be stored in userDB
		DatabaseEntry theKey = new DatabaseEntry(username.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		try {
			// Insert key-value pair in userDB
			OperationStatus os = this.getUserDB().get(txn, theKey, theVal, LockMode.DEFAULT);
			
			if (os == OperationStatus.NOTFOUND) {
				theVal = null;
			}
			
			// Commit the transaction
			txn.commit();
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
		
		// Convert database entry to UserVal
		UserInfo res = null;
		if (theVal != null) {
			res = (UserInfo) this.getUserInfoBinding().entryToObject(theVal);
		} 
		return res;
	}
	
	// Method for putting doc info to the DB
	public void addDocInfo(String URL, DocInfo info)
	{
		// Declare the database entry key-value pair that need to be stored in docDB
		DatabaseEntry theKey = new DatabaseEntry(URL.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();
		
		// Associate the bindings with the database entries
		this.getDocInfoBinding().objectToEntry(info, theVal);

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		
		try {
			// Insert key-value pair in userDB
			this.getDocDB().put(txn, theKey, theVal);
			
			// Commit the transaction
			txn.commit();
			
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
	}
	
	// Method for retrieving doc info from docDB
	public DocInfo getDocInfo(String URL)
	{
		// Declare the database entry key-value pair
		DatabaseEntry theKey = new DatabaseEntry(URL.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		try {
			// Insert key-value pair in docDB
			OperationStatus os = this.getDocDB().get(txn, theKey, theVal, LockMode.DEFAULT);
			
			if (os == OperationStatus.NOTFOUND) {
				theVal = null;
			}
			
			// Commit the transaction
			txn.commit();
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
		
		// Convert database entry to DocVal
		DocInfo res = null;
		if (theVal != null) {
			res = (DocInfo) this.getDocInfoBinding().entryToObject(theVal);
		} 
		return res;
	}
	
	// Method for putting channel info to the DB
	public void addChannelInfo(String channelName, ChannelInfo info)
	{
		// Declare the database entry key-value pair that need to be stored in channelDB
		DatabaseEntry theKey = new DatabaseEntry(channelName.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();
			
		// Associate the bindings with the database entries
		this.getChannelInfoBinding().objectToEntry(info, theVal);

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
			
		try {
			// Insert key-value pair in userDB
			this.getChannelDB().put(txn, theKey, theVal);
				
			// Commit the transaction
			txn.commit();
				
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
	}
		
	// Method for retrieving channel info from channelDB
	public ChannelInfo getChannelInfo(String channelName)
	{
		// Declare the database entry key-value pair
		DatabaseEntry theKey = new DatabaseEntry(channelName.getBytes());
		DatabaseEntry theVal = new DatabaseEntry();

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		try {
			// Insert key-value pair in docDB
			OperationStatus os = this.getChannelDB().get(txn, theKey, theVal, LockMode.DEFAULT);
				
			if (os == OperationStatus.NOTFOUND) {
				theVal = null;
			}
				
			// Commit the transaction
			txn.commit();
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
			
		ChannelInfo res = null;
		if (theVal != null) {
			res = (ChannelInfo) this.getChannelInfoBinding().entryToObject(theVal);
		} 
		return res;
	}
	
	
	// Method for removing channel info from channelDB
	public boolean deleteChannelInfo(String channelName)
	{
		// Declare the database entry key-value pair
		DatabaseEntry theKey = new DatabaseEntry(channelName.getBytes());

		// Begin transaction
		Transaction txn = this.getEnv().beginTransaction(null, null);
		
		// Boolean for status
		boolean success = false;
		try {
			// Insert key-value pair in docDB
			OperationStatus os = this.getChannelDB().delete(txn, theKey);
				
			if (os == OperationStatus.SUCCESS) {
				success = true;
			}
				
			// Commit the transaction
			txn.commit();
		} catch(Exception e) {
			if (txn != null)
			{
				txn.abort();
				txn = null;
			}
		}
			
		return success;
	}
	
	// Get a list of channels from channelDB
	public List<String> getURLs()
	{
		List<String> URLs = new LinkedList<String>();
		Cursor cursor = null;
		
		// Declare the database entry key-value pair
		DatabaseEntry theKey = new DatabaseEntry();
		DatabaseEntry theVal = new DatabaseEntry();

		try {
			cursor = this.getDocDB().openCursor(null, null);
			
			while (cursor.getNext(theKey, theVal, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				String channelName = new String(theKey.getData());
				URLs.add(channelName);
			}
			
		} catch(Exception e) {
			// Do nothing
		} finally {
			cursor.close();
		}
		return URLs;
	}
}
