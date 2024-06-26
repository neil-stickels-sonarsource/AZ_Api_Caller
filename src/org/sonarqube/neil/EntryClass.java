package org.sonarqube.neil;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.sonarqube.neil.FindNonSLUsers.SQUser;
/**
 * The main entry point for the jar file.  As mentioned in the expected usage, there are 4 parameters:
 * 		1 - The token used to connect to Sonar (recommended this token is generated by someone with Admin access
 * 		2 - The base URL of your SonarQube instance (for example: https://nautilus.sonarqube.org)
 * 		3 - The output file results will be written
 * 		4 - Which API you are using.  There are two options for this:
 * 				users - will determine which users have logged into SonarQube in the last 90 days but 
 * 						have not connected to SonarLint in connected mode in the last 90 days
 * 				secrets - will iterate through all of your projects, and find all of the issues found
 * 						specified to Secrets detection rules
 * 
 * Note that the later option, re: secrets, will take some time to run, as it has to go through every issue from
 * every branch of every project to determine what type of issue it is.  Depending on how many projects and how
 * many issues you have, this can take some time.  I added some console logging to show you which project it is
 * on so that you can see it is not stuck or frozen.  Branches with 10s of thousands of issues could take a minute
 * or more per branch to complete.
 */
public class EntryClass {
	
	private static final Integer DAYS_SINCE_SQ_LOGIN = 90;
	private static final Integer DAYS_SINCE_SL_CONNECTION = 90;

	public static void main(String[] args) 
	{
		if(args.length != 4)
		{
			System.out.println("Expected usage: java -jar AZApiCaller.jar {1} {2} {3} {4}\nwhere: \n\t {1} is your sonar token,\n\t {2} is your SonarQube URL,\n\t {3} is the name of the file to save results,\n\t {4} is either \"users\" or \"secrets\"");
			System.exit(0);
		}
		String token = args[0]; // user token to login to Sonar API
		String url = args[1]; // base URL for your SonarQube instance
		String fileName = args[2]; // name of the file to write the output
		String api = args[3]; // which api to call
		
		if(api.equalsIgnoreCase("users"))
		{
			FindNonSLUsers fnslu = new FindNonSLUsers(token, url);
			List<SQUser> users = fnslu.getNonSonarLintUsers(DAYS_SINCE_SQ_LOGIN, DAYS_SINCE_SL_CONNECTION);
			writeUsersToFile(fileName, users);
		} else if(api.equalsIgnoreCase("secrets"))
		{
			FindSecrets fs = new FindSecrets(token, url);
			fs.findSecrets();
			writeSecretsToFile(fileName, fs.getSecretsDetected());
		}


	}

	private static void writeSecretsToFile(String fileName, List<String> secretsDetected) {
		FileWriter fw = null;
		try
		{
			fw = new FileWriter(fileName);
			// write all of the header information for the file		
			fw.write("projectKey,branch,fileName,rule,status,message,author");
			fw.write("\n");
			fw.flush();
			// iterate through each Secrets detection issue and write out details
			for(String secret: secretsDetected)
			{
				fw.write(secret);
				fw.write("\n");
				fw.flush();
			}			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} finally
		{
			if(fw != null)
			{
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

	private static void writeUsersToFile(String fileName, List<SQUser> users) {
		FileWriter fw = null;
		try
		{
			fw = new FileWriter(fileName);
			// write all of the header information for the file
			fw.write("name,login,lastSonarQubeDate,lastSonarQubeDays,lastSonarLintDate,lastSonarLintDays");
			fw.write("\n");
			fw.flush();
			// iterate through each User and write out details
			for(SQUser user: users)
			{
				fw.write(user.toString());
				fw.write("\n");
				fw.flush();
			}			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		} finally
		{
			if(fw != null)
			{
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		
	}

}
