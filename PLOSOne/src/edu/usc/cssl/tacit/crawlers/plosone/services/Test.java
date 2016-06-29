package edu.usc.cssl.tacit.crawlers.plosone.services;

import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;	

public class Test {

	public static void main(String[] args)throws Exception {
		invokePLOSCrawler();
	}
	
	public static void invokePLOSCrawler() throws Exception{
		SolrServer solrServer = new HttpSolrServer(PLOSOneWebConstants.BASE_URL);
		
		SolrQuery solrQuery =  new SolrQuery();
		solrQuery.addField(PLOSOneWebConstants.FIELD_BODY)
		.setParam(PLOSOneWebConstants.FEATURE_APIKEY, PLOSOneWebConstants.TEST_API_KEY)
		.setRows(100)
		.setQuery("computational biology")
		.addField(PLOSOneWebConstants.FIELD_AUTHOR);
		
		try {
			QueryResponse queryResponse = solrServer.query(solrQuery);
			System.out.println("1");
			System.out.println("done"+queryResponse.toString());
			System.out.println("2");
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
