package org.sonarqube.neil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Used to find users who have logged in to SonarQube recently but have not connected with SonarLint in
 * connected mode.  This class is called from the EntryClass and will create a List of SQUser objects
 * which contains information on all such users.
 */
public class FindNonSLUsers {
	
	private final String token;
	private final String hostURL;
	
	public FindNonSLUsers(String token, String url)
	{
		this.token = token;
		if(url.endsWith("/"))
			url = url.substring(0, url.length()-1);
		this.hostURL = url;
	}
	
	private int getTotalUsers()
	{
		try {
			URL url = new URL(hostURL+"/api/v2/users-management/users?q=&pageSize=0");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", "Bearer "+token);
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() != 200)
			{
				System.out.println(url+" returned "+conn.getResponseCode());
				throw new RuntimeException("getTotalUsers failed! HTTP error code "+conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while((line = br.readLine()) != null)
				response.append(line);
			conn.disconnect();
			JSONObject json = new JSONObject(response.toString());
			JSONObject page = json.getJSONObject("page");
			return page.getInt("total");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	/**
	 * Get a list of all SonarQube users who have logged into SonarQube recently but have not connected
	 * with SonarLint within the specified time
	 * @param lastSQLoginDays - number of days to consider the user to have recently logged in to SonarQube
	 * @param lastSLConnectionDays - number of days to consider the user to have recently connected with SonarLint
	 * @return all of the users who have connected to SonarQube within the specified time, but have not connected
	 * 		to SonarLint within the specified time
	 */
	public List<SQUser> getNonSonarLintUsers(int lastSQLoginDays, int lastSLConnectionDays)
	{
		List<SQUser> nonSLUsers = new ArrayList<>();
		int totalUsers = getTotalUsers();
		if(totalUsers == 0)
			return nonSLUsers;
		int pageCounter = 1;
		int remainingUsers = totalUsers;
		int pageSize = 50;
		while(remainingUsers > 0)
		{
			nonSLUsers.addAll(getNextBatch(pageCounter, pageSize, lastSQLoginDays, lastSLConnectionDays));
			remainingUsers-=pageSize;
			pageCounter++;
		}
		return nonSLUsers;
	}

	private List<SQUser> getNextBatch(int pageCounter, int pageSize, int lastSQLoginDays, int lastSLConnectionDays) 
	{
		List<SQUser> thisUsers = new ArrayList<>();
		try {
			URL url = new URL(hostURL+"/api/v2/users-management/users?q=&pageSize="+pageSize+"&pageIndex="+pageCounter);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", "Bearer "+token);
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() != 200)
			{
				System.out.println(url+" returned "+conn.getResponseCode());
				throw new RuntimeException("getNextBatch failed! HTTP error code "+conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while((line = br.readLine()) != null)
				response.append(line);
			conn.disconnect();
			JSONObject json = new JSONObject(response.toString());
			JSONArray users = json.getJSONArray("users");
			for(int i = 0; i < users.length(); i++)
			{
				JSONObject user = users.getJSONObject(i);
				String login = user.getString("login");
				String name = user.getString("name");
				String lastSQConnDate = user.optString("sonarQubeLastConnectionDate",null);
				String lastSLConnDate = user.optString("sonarLintLastConnectionDate",null);
				Date lastSQConnection = null;
				Date lastSLConnection = null;
				if(lastSQConnDate != null)
					lastSQConnection = stringToDate(lastSQConnDate);
				if(lastSLConnDate != null)
					lastSLConnection = stringToDate(lastSLConnDate);
				SQUser sqUser = new SQUser();
				sqUser.setLogin(login);
				sqUser.setName(name);
				sqUser.setLastSQConnection(lastSQConnection);
				sqUser.setLastSLConnection(lastSLConnection);
				int lastSQConnDays = (int) sqUser.calcLastSQConnDays();
				sqUser.setLastSQConnDays(lastSQConnDays);
				int lastSLConnDays = (int) sqUser.calcLastSLConnDays();
				sqUser.setLastSLConnDays(lastSLConnDays);
				if(lastSQConnDays < lastSQLoginDays && lastSLConnDays > lastSLConnectionDays)
					thisUsers.add(sqUser);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return thisUsers;
	}
	
	private Date stringToDate(String strDate) throws ParseException 
	{
		if(strDate == null)
			return null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss");
		Date date = sdf.parse(strDate);
		return date;
	}

	/**
	 * Inner class used to hold the information for users.
	 */
	class SQUser
	{
		String name;
		String login;
		Date lastSQConnection;
		Date lastSLConnection;
		int lastSQConnDays;
		int lastSLConnDays;
		
		public SQUser()
		{
			
		}
		
		public SQUser(String name, String login, Date lastSQConnection, Date lastSLConnection, 
				int lastSQConnDays, int lastSLConnDays)
		{
			this.name = name;
			this.login = login;
			this.lastSQConnection = lastSQConnection;
			this.lastSLConnection = lastSLConnection;
			this.lastSQConnDays = lastSQConnDays;
			this.lastSLConnDays = lastSLConnDays;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getLogin() {
			return login;
		}

		public void setLogin(String login) {
			this.login = login;
		}

		public Date getLastSQConnection() {
			return lastSQConnection;
		}

		public void setLastSQConnection(Date lastSQConnection) {
			this.lastSQConnection = lastSQConnection;
		}

		public Date getLastSLConnection() {
			return lastSLConnection;
		}

		public void setLastSLConnection(Date lastSLConnection) {
			this.lastSLConnection = lastSLConnection;
		}

		public int getLastSQConnDays() {
			return lastSQConnDays;
		}

		public void setLastSQConnDays(int lastSQConnDays) {
			this.lastSQConnDays = lastSQConnDays;
		}

		public int getLastSLConnDays() {
			return lastSLConnDays;
		}

		public void setLastSLConnDays(int lastSLConnDays) {
			this.lastSLConnDays = lastSLConnDays;
		}
		
		public long calcLastSQConnDays()
		{
			if(lastSQConnection == null)
				return Integer.MAX_VALUE;
			Calendar now = Calendar.getInstance();
			Date today = now.getTime();
			
			long diffInMillies = Math.abs(today.getTime() - lastSQConnection.getTime());
			long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
			return diff;
		}
		
		public long calcLastSLConnDays()
		{
			if(lastSLConnection == null)
				return Integer.MAX_VALUE;
			Calendar now = Calendar.getInstance();
			Date today = now.getTime();
			
			long diffInMillies = Math.abs(today.getTime() - lastSLConnection.getTime());
			long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
			return diff;
		}
		
		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(name);
			str.append(",");
			str.append(login);
			str.append(",");
			str.append(lastSQConnection);
			str.append(",");
			str.append(lastSQConnDays);
			str.append(",");
			if(lastSLConnection == null)
				str.append("Never,Never");
			else
			{
				str.append(lastSLConnection);
				str.append(",");
				str.append(lastSLConnDays);
			}
			return str.toString();
		}
	}
	
	

}
