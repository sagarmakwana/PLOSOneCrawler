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
	
	public final static int DOCUMENTS_PER_RESPONSE_PAGE = 10;

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
	 * 
	 * @param inputQuery
	 * @return
	 */
	public static String getModifiedQuery(String inputQuery){
		
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
		
		return modifiedStringBuilder.toString();
	}
	
	public static JSONArray buildPagedResponses(Map<String, String> urlFeatures){
		JSONArray overallResponse = new JSONArray();
		int numOfRows = getNumOfRows(buildURL(urlFeatures));
		
		int numOfRequests = numOfRows/DOCUMENTS_PER_RESPONSE_PAGE ;
		
		if (numOfRows%DOCUMENTS_PER_RESPONSE_PAGE != 0){
			numOfRequests++; //Extra request for the leftover documents
		}
		
		String singleResponse;
		int startIndex = 0; 
		for(int i = 0; i < numOfRequests; i++){
			singleResponse = getHTTPResponse(buildURL(urlFeatures));
			
			//TODO:  Extract the documents from single response and add it to the overall response
			
			startIndex += DOCUMENTS_PER_RESPONSE_PAGE; //Updating the start index for the next request
			urlFeatures.put(PLOSOneWebConstants.FEATURE_START, startIndex+"");
		}
		
		
		
		return overallResponse;	
	}
	
	
	public static void main(String args[]) throws IOException{
		
		//Add all the fields that are required in the output
		List<String> outputFields = new ArrayList<String>();
		outputFields.add(PLOSOneWebConstants.FIELD_AUTHOR);
		outputFields.add(PLOSOneWebConstants.FIELD_TITLE);
		outputFields.add(PLOSOneWebConstants.FIELD_ABSTRACT);
		outputFields.add(PLOSOneWebConstants.FIELD_BODY);

		Map<String,String> urlFeatures = new HashMap<String,String>();
		urlFeatures.put(PLOSOneWebConstants.FEATURE_APIKEY, PLOSOneWebConstants.TEST_API_KEY);
		urlFeatures.put(PLOSOneWebConstants.FEATURE_DOCTYPE, "json");
		urlFeatures.put(PLOSOneWebConstants.FEATURE_FIELDS, getOutputFields(outputFields));
		urlFeatures.put(PLOSOneWebConstants.FEATURE_FILTER_QUERY, "doc_type:full");
		urlFeatures.put(PLOSOneWebConstants.FEATURE_QUERY, getModifiedQuery("machine learning;neuralnets"));
		//This is the number of documents in a single paged response. Several pages need to be combined.
		urlFeatures.put(PLOSOneWebConstants.FEATURE_ROWS, DOCUMENTS_PER_RESPONSE_PAGE + ""); 
		//Initialize start to 0.
		urlFeatures.put(PLOSOneWebConstants.FEATURE_START, "0");
		
		String output = getHTTPResponse(buildURL(urlFeatures));
		Date dateObj = new Date();
		DateFormat df = new SimpleDateFormat("MM-dd-yy-HH-mm-ss");
		FileWriter fileWriter = new FileWriter(new File("/Users/CSSLadmin/Desktop/TestOutput/plos_"+df.format(dateObj)+".txt"));
		
		fileWriter.write(output);
		
		fileWriter.close();
		
	}
}
