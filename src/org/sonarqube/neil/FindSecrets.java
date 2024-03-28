package org.sonarqube.neil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Used to find all of the issues in SonarQube which are generated from the Secrets detection rules.
 */
public class FindSecrets {
	
	private final String token;
	private final String hostURL;
	
	private List<String> secretsDetected = new ArrayList<>();
	
	public FindSecrets(String token, String url)
	{
		this.token = token;
		if(url.endsWith("/"))
			url = url.substring(0, url.length()-1);
		this.hostURL = url;
	}
	
	/**
	 * There is a few steps this will take when called:
	 * 1. Find all of the projects in SonarQube
	 * 2. Find all of the branches for each of these projects
	 * 3. Find all of the issues for each branch of each project
	 * 4. Determine which of these issues are related to Secrets detection rules
	 * 5. Any such issues get stored in the secretsDetected List
	 */
	public void findSecrets()
	{
		//get all of the project keys
		List<String> projectKeys = getProjectsPage(1);
		System.out.println("there are "+projectKeys.size()+" projects");
		//get all of the secrets for these projects
		getSecretsForProjects(projectKeys);
	}
	
	private void getSecretsForProjects(List<String> projectKeys) {
		for(String projectKey: projectKeys)
		{
			System.out.println("Finding secrets for "+projectKey);
			// first we have to get all of the branches for this project
			List<String> branches = getBranchesForProject(projectKey);
			//System.out.println("there are "+branches.size()+" branches");
			// now iterate over the branches and get all of the issues for each branch, specifically 
			// filtering this list of issues to just secrets detection issues
			for(String branch: branches)
			{
				getSecretsForBranch(projectKey,branch);
			}
		}
		
	}

	private void getSecretsForBranch(String projectKey, String branch) 
	{
		try {
			URL url = new URL(hostURL+"/api/projects/export_findings?project="+projectKey+"&branch="+branch);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", "Bearer "+token);
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() != 200)
			{
				System.out.println(url+" returned "+conn.getResponseCode());
				throw new RuntimeException("getSecretsForBranch failed! HTTP error code "+conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while((line = br.readLine()) != null)
				response.append(line);
			conn.disconnect();
			JSONObject json = new JSONObject(response.toString());
			JSONArray findings = json.getJSONArray("export_findings");
			for(int i = 0; i < findings.length(); i++)
			{
				JSONObject finding = findings.getJSONObject(i);
				String rule = finding.getString("ruleReference");
				if(rule.startsWith("secrets"))
				{
					Finding f = new Finding();
					f.setProjectKey(projectKey);
					f.setBranch(branch);
					f.setRule(rule);
					f.setFileName(finding.getString("path"));
					f.setStatus(finding.getString("issueStatus"));
					f.setMessage(finding.getString("message"));
					f.setAuthor(finding.getString("author"));
					//f.setAssignee(finding.getString("assignee"));
					secretsDetected.add(f.toString());
				}
			}

			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public List<String> getSecretsDetected()
	{
		return secretsDetected;
	}

	private List<String> getBranchesForProject(String projectKey) 
	{
		List<String> branches = new ArrayList<>();
		try {
			URL url = new URL(hostURL+"/api/project_branches/list?project="+projectKey);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", "Bearer "+token);
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() != 200)
			{
				System.out.println(url+" returned "+conn.getResponseCode());
				throw new RuntimeException("getBranchesForProjecct failed! HTTP error code "+conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while((line = br.readLine()) != null)
				response.append(line);
			conn.disconnect();
			JSONObject json = new JSONObject(response.toString());
			JSONArray branchesArray = json.getJSONArray("branches");
			for(int i = 0; i < branchesArray.length(); i++)
			{
				JSONObject branch = branchesArray.getJSONObject(i);
				branches.add(branch.getString("name"));
			}

			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return branches;
	}

	private List<String> getProjectsPage(int pageNum)
	{
		List<String> projectKeys = new ArrayList<>();
		try {
			URL url = new URL(hostURL+"/api/projects/search?p="+pageNum);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", "Bearer "+token);
			conn.setRequestMethod("GET");
			if(conn.getResponseCode() != 200)
			{
				System.out.println(url+" returned "+conn.getResponseCode());
				throw new RuntimeException("getProjectsPage failed! HTTP error code "+conn.getResponseCode());
			}
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while((line = br.readLine()) != null)
				response.append(line);
			conn.disconnect();
			JSONObject json = new JSONObject(response.toString());
			JSONObject paging = json.getJSONObject("paging");
			int total = paging.getInt("total");
			boolean again = (total > pageNum*100);
			JSONArray projects = json.getJSONArray("components");
			for(int i = 0; i < projects.length(); i++)
			{
				JSONObject project = projects.getJSONObject(i);
				projectKeys.add(project.getString("key"));
			}
			if(again)
				projectKeys.addAll(getProjectsPage(++pageNum));
			return projectKeys;
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return projectKeys;
	}
	
	/** 
	 * Inner class for storing the issues found that were generated from Secrets rules.
	 */
	class Finding
	{
		String projectKey;
		String branch;
		String fileName;
		String message;
		String status;
		String author;
		String assignee;
		String rule;
		
		public Finding()
		{
			
		}

		public Finding(String projectKey, String branch, String fileName, String message, String status, String author,
				String assignee, String rule) {
			super();
			this.projectKey = projectKey;
			this.branch = branch;
			this.fileName = fileName;
			this.message = message;
			this.status = status;
			this.author = author;
			this.assignee = assignee;
			this.rule = rule;
		}

		public String getProjectKey() {
			return projectKey;
		}

		public void setProjectKey(String projectKey) {
			this.projectKey = projectKey;
		}

		public String getBranch() {
			return branch;
		}

		public void setBranch(String branch) {
			this.branch = branch;
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getAuthor() {
			return author;
		}

		public void setAuthor(String author) {
			this.author = author;
		}

		public String getAssignee() {
			return assignee;
		}

		public void setAssignee(String assignee) {
			this.assignee = assignee;
		}

		public String getRule() {
			return rule;
		}

		public void setRule(String rule) {
			this.rule = rule;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(projectKey);
			str.append(",");
			str.append(branch);
			str.append(",");
			str.append(fileName);
			str.append(",");
			str.append(rule);
			str.append(",");
			str.append(status);
			str.append(",");
			str.append(message);
			str.append(",");
			str.append(author);
			//str.append(",");
			//str.append(assignee);
			return str.toString();
		}
		
		
	}

}
