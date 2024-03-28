# AZ_Api_Caller

Note: using this requires SonarQube 9.1 or higher, as one of the APIs used was introduced in that release.

Helper to call the SonarQube API and generate one of two reports:
  - A list of users who have logged into SonarQube in the last 90 days, but have not connected to SonarQube from SonarLint with connected mode
  - A list of issues from all projects which were created due to the Secrets detection rules

Precompiled executable AZApiCaller.jar is provided. To run this:

java -jar AZApiCaller arg1 arg2 arg3 arg4

where:

arg1 is your Sonar Token to access the API (this user should have administrator access to SonarQube or you will likely get 403 errors)
arg2 is your base URL to your SonarQube instance
arg3 is the name of the file you want to write the results into
arg4 is either "users" or "secrets" depending on which file you want to generate

Also note that calling this with the "secrets" option might take some time as it is pulling every issue from every branch of every project.  Because of this, there is some additional logging on the console to indicate each project being scanned.
