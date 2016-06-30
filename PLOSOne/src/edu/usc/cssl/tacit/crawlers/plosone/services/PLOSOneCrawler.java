package edu.usc.cssl.tacit.crawlers.plosone.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PLOSOneCrawler {
	
	public final static int MAXIMUM_NETWORK_CALL_REATTEMPTS = 20;
	
	public final static int DOCUMENTS_PER_RESPONSE_PAGE = 100;

	/**
	 * This method returns the JSON HTTP response string for the input string URL 
	 * @param url Connection URL
	 * @return JSON response string if the connection was successful and response was received else it returns an empty string.
	 */
	private static String getHTTPResponse(String url) {
		
		BufferedReader br = null;
		HttpURLConnection con = null;
		String inputLine;
		StringBuffer response = new StringBuffer();
		try{
			URL obj = new URL(url);
			
			int responseCode = 500; //To enter the while loop
			int i = 0;
			//This loop continuous to hit the URL until the maximum number of network call re-attempts are done or the response code 200 is received, whichever is first. 
			while (i <= MAXIMUM_NETWORK_CALL_REATTEMPTS && responseCode != HttpURLConnection.HTTP_OK){
				
				con = (HttpURLConnection) obj.openConnection();

				con.setRequestMethod("GET");
				con.setReadTimeout(120000);
				con.setConnectTimeout(60000);
				con.setDoInput(true);
				
				responseCode = con.getResponseCode();
				System.out.println("\nSending 'GET' request to URL : " + url);
				System.out.println("Response Code : " + responseCode);
			
				if(responseCode == HttpURLConnection.HTTP_OK){

					br = new BufferedReader(new InputStreamReader(con.getInputStream()));
					while ((inputLine = br.readLine()) != null) {
						response.append(inputLine);
					}
					br.close();
					return response.toString();
				}else{
					System.out.println("Internal Connection Error.");
					i++;
					
				}	
				if (con != null){
					con.disconnect();
				}
			}
			return "";
			
		}catch(SocketTimeoutException e){
			System.out.println("Connection is taking too long.There is something wrong with the server.");
			System.out.println(e.getMessage());
			return "";
		}catch(UnknownHostException e){
			System.out.println("There seems to be no internet connection.");
			System.out.println(e.getMessage());
			return "";
		}catch(Exception e){
			System.out.println(e.getMessage());
			return "";
		}
		

	}
	
	/**
	 * This method hits the API URL, extracts and returns the number of rows in the results. 
	 * @param url 
	 * @return
	 */
	private static int getNumOfRows(String url){
		String jsonResponse = getHTTPResponse(url);
		JSONObject resultJSONObject;
		int numOFRows = 0;
		if (!jsonResponse.equals("")){
			resultJSONObject = new JSONObject(jsonResponse);
			try{
				numOFRows = resultJSONObject.getJSONObject("response").getInt("numFound");
			}catch(JSONException e){
				return 0;
			}
		}else{
			return 0;
		}
		return numOFRows;
	}
	
	/**
	 * This method builds the URL using the provided URL features and the Search Fields. 
	 * @param urlFeatures
	 * @param searchQueryFields
	 * @return
	 */
	private static String buildURL(Map<String, String> urlFeatures){
		
		StringBuilder url = new StringBuilder();
		
		url.append(PLOSOneWebConstants.BASE_URL + PLOSOneWebConstants.QUERY_SEPARATOR);
		
		if (urlFeatures != null){
			Iterator<Entry<String,String>> iterator = urlFeatures.entrySet().iterator();
			while(iterator.hasNext()){
				Entry<String,String> entry= iterator.next();
				url.append(entry.getKey()+"="+entry.getValue()+"&");
			}
			
			url.delete(url.length()-1, url.length());
		}
		
		return url.toString();
		
	}
	
	
	/**
	 * This method converts the list of fields into a comma separated string of fields ex. title,author,score
	 * @param fieldList List of fields that are expected in the output JSON file.
	 * @return Comma separated string of fields
	 */
	public static String getOutputFields(List<String> fieldList){
		
		StringBuilder fieldStringBuilder = new StringBuilder();
		Iterator<String> iterator = fieldList.iterator();
		
		while(iterator.hasNext()){
			String field= iterator.next();
			fieldStringBuilder.append(field+",");
		}
		
		fieldStringBuilder.delete(fieldStringBuilder.length()-1, fieldStringBuilder.length());
		
		return fieldStringBuilder.toString();
		
	}
	
	/**
	 * This method converts the map of query fields into a AND separated string of fields ex. title,author,score
	 * @param queryMap
	 * @return
	 */
	public static String getQueryFields(Map<String,String> queryMap){
		
		StringBuilder queryStringBuilder = new StringBuilder();
	
		Iterator<Entry<String,String>> interator = queryMap.entrySet().iterator();
		while(interator.hasNext()){
			Entry<String,String> entry= interator.next();
			queryStringBuilder.append(entry.getKey()+":"+entry.getValue()+"%20AND%20");
		}
		
		queryStringBuilder.delete(queryStringBuilder.length()-9, queryStringBuilder.length());
		
		return queryStringBuilder.toString();
	}
	
	/**
	 * This method converts the raw query given by the user and converts it into the format required by the API.
	 * @param tag It indicates in what section the search has to be concentrated.
	 * @param inputQuery Raw input query from the user.
	 * @return
	 */
	public static String getModifiedQuery(String tag,String inputQuery){
		
		StringBuilder modifiedStringBuilder = new StringBuilder("");
		String keywords[] = inputQuery.split(";");
		for(String keyword : keywords){
			keyword = keyword.trim().toLowerCase();
			if (keyword.equals("")){
				continue;
			}
			if (keyword.contains(" ")){
				keyword = "\"" + keyword + "\"";				
			}
			
			keyword = keyword.replaceAll(" ", "%20");
			
			modifiedStringBuilder.append(keyword+"%20OR%20");
			
		}
		
		if (modifiedStringBuilder.length() > 0 ){
			modifiedStringBuilder.delete(modifiedStringBuilder.length()-8, modifiedStringBuilder.length());
		}
		
		return tag+":"+modifiedStringBuilder.toString();
	}
	
	
	/**
	 * This method breaks the request into several pages and writes the response of each request to the input file.
	 * @param urlFeatures
	 * @param noOfDocuments
	 * @param fileWriter
	 * @throws IOException
	 */
	public static void writePagedResponses(Map<String, String> urlFeatures,int noOfDocuments, FileWriter fileWriter)throws IOException{

		
		boolean leftover = false;
		
		//If the user does not indicate the number of documents then by default it should download all the documents
		if (noOfDocuments == -1){
			noOfDocuments = getNumOfRows(buildURL(urlFeatures));
		}
		 
		int numOfRequests = noOfDocuments/DOCUMENTS_PER_RESPONSE_PAGE ;
		
		if (noOfDocuments%DOCUMENTS_PER_RESPONSE_PAGE != 0){
			numOfRequests++; //Extra request for the leftover documents
			leftover = true;
		}
		
		//This condition ensures that the file is written only if atleast one request is to be made.
		if (numOfRequests != 0){
			fileWriter.write("[");
		}
		
		String singleResponse;
		int startIndex = 0; 
		for(int i = 0; i < numOfRequests; i++){
			System.out.println(startIndex);
			
			//This condition ensures that only the required number of documents are downloaded in the last request.
			if (i == numOfRequests-1 && leftover){
				urlFeatures.put(PLOSOneWebConstants.FEATURE_ROWS, (noOfDocuments%DOCUMENTS_PER_RESPONSE_PAGE)+"");
			}
			
			singleResponse = getHTTPResponse(buildURL(urlFeatures));
			

			JSONObject singleResponseDocuments = new JSONObject(singleResponse);
			singleResponse = singleResponseDocuments.getJSONObject("response").getJSONArray("docs").toString();
			singleResponse = singleResponse.substring(singleResponse.indexOf("[")+1,singleResponse.lastIndexOf("]")).trim();
			
			//This condition checks for the last request.
			if (i == numOfRequests-1){
				fileWriter.write(singleResponse + "]");
			}else{
				fileWriter.write(singleResponse + ", ");
			}
			
			startIndex += DOCUMENTS_PER_RESPONSE_PAGE; //Updating the start index for the next request
			urlFeatures.put(PLOSOneWebConstants.FEATURE_START, startIndex+"");
		}
			
	}
	
	
	public static void main(String args[]) throws IOException{
		
		//Add all the fields that are required in the output
		List<String> outputFields = new ArrayList<String>();
		outputFields.add(PLOSOneWebConstants.FIELD_AUTHOR);
		outputFields.add(PLOSOneWebConstants.FIELD_TITLE);
		outputFields.add(PLOSOneWebConstants.FIELD_ABSTRACT);
		outputFields.add(PLOSOneWebConstants.FIELD_BODY);
		
		
		//Building the url features for hitting the API
		Map<String,String> urlFeatures = new HashMap<String,String>();
		urlFeatures.put(PLOSOneWebConstants.FEATURE_APIKEY, PLOSOneWebConstants.TEST_API_KEY);
		urlFeatures.put(PLOSOneWebConstants.FEATURE_DOCTYPE, "json");
		urlFeatures.put(PLOSOneWebConstants.FEATURE_FIELDS, getOutputFields(outputFields));
		urlFeatures.put(PLOSOneWebConstants.FEATURE_FILTER_QUERY, "doc_type:full");
		urlFeatures.put(PLOSOneWebConstants.FEATURE_QUERY, getModifiedQuery(PLOSOneWebConstants.FIELD_EVERYTHING,"machine learning;neuralnets"));
		//This is the number of documents in a single paged response. Several pages need to be combined.
		urlFeatures.put(PLOSOneWebConstants.FEATURE_ROWS, DOCUMENTS_PER_RESPONSE_PAGE + ""); 
		//Initialize start to 0.
		urlFeatures.put(PLOSOneWebConstants.FEATURE_START, "0");
		
		
		Date dateObj = new Date();
		DateFormat df = new SimpleDateFormat("MM-dd-yy-HH-mm-ss");
		FileWriter fileWriter = new FileWriter(new File("/Users/CSSLadmin/Desktop/TestOutput/plos_"+df.format(dateObj)+".json"));
		writePagedResponses(urlFeatures,210,fileWriter);
		fileWriter.close();
		
		
	}
}
