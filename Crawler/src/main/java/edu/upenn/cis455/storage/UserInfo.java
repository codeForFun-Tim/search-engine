package edu.upenn.cis455.storage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 * Custom class for user account info
 *
 */
public class UserInfo implements Serializable {
	
	private static final long serialVersionUID = -5552801330389679710L;
	private String firstName;
	private String lastName;
	private byte[] hashedPwd;
	private List<String> channels = new LinkedList<String>();
	
	public UserInfo(String f, String l, byte[] p) {
		this.firstName = f;
		this.lastName = l;
		this.hashedPwd = p;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public boolean checkPwdEqual(byte[] input) {
		return Arrays.equals(input, hashedPwd);
	}
	
	public void subscribe(String channelName) {
		this.channels.add(channelName);
	}
	
	public void unsubscribe(String channelName) {
		if (this.channels.contains(channelName)) {
			this.channels.remove(channelName);
		}
	}
	
	public boolean ifSubscribed(String channelName) {
		return this.channels.contains(channelName);
	}
}
