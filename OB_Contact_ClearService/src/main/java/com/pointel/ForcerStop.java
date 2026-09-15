package com.pointel;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.ApiRequest;
import com.mypurecloud.sdk.v2.ApiRequestBuilder;
import com.mypurecloud.sdk.v2.ApiResponse;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;
import com.mypurecloud.sdk.v2.api.OutboundApi;
import com.mypurecloud.sdk.v2.model.CampaignEntityListing;

public class ForcerStop {

	public static void main(String[] args) throws ApiException, Exception {
		// TODO Auto-generated method stub
		ForcerStop f = new ForcerStop();
		//AppService service = new AppService();
		f.AppService();
		String campaignId = f.getCampaignIdByName("scg_ccc_cmpg_voice_baddog_dev");
		//https://api.usw2.pure.cloud/api/v2/outbound/campaigns/${campaignId}
		System.out.println("campaignId "+campaignId);
		f.forceStopSafely(campaignId);
		String contactListInfo = f.getContactListInfo("scg_ccc_cmpg_voice_baddog_dev");
		String[] contactListArray = contactListInfo.split("\\|");
		String contactListId = contactListArray[0];
		
		f.clearConactList(contactListId);
		

	}
	
	public String getContactListInfo(String campaignName) {
		OutboundApi apiInstance = new OutboundApi();
		String contactListInfo = "";
		try {
			////AppController.logger
					//.log("[POINTEL] - getContactListInfo() - Get Campaign Id and ContactListId Process Started");
			Integer pageSize = 100;
			Integer pageNumber = 1;
			String filterType = "Equals";
			CampaignEntityListing result = apiInstance.getOutboundCampaigns(pageSize, pageNumber, filterType,
					campaignName, null, null, null, null, null, null, null, null, null);
			if (!result.getEntities().isEmpty() && result.getEntities().get(0).getContactList() != null) {
				
				String contactListId = result.getEntities().get(0).getContactList().getId();
				String contactListName = result.getEntities().get(0).getContactList().getName();
				contactListInfo = contactListId+"|"+contactListName;
				//AppController.logger.log("[POINTEL] - getContactListInfo() - contactListName " + contactListName);
			}
			//AppController.logger.log("[POINTEL] - getContactListInfo() - ContactList info- " + contactListInfo);
			return contactListInfo;
		} catch (ApiException apiException) {
			//AppController.logger.log("[POINTEL] - getContactListInfo() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return contactListInfo;
	}

	
	public void clearConactList(String contactListId) throws Exception, ApiException {
		try {
			OutboundApi apiInstance = new OutboundApi();
			////AppController.logger.log("[POINTEL] - clearConactList() - Clear the contents of a contact list -"+ contactListId + "  Process Started");
			apiInstance.postOutboundContactlistClear(contactListId);
			////AppController.logger.log("[POINTEL] - clearConactList() - ContactList cleared Successfully");
		} catch (ApiException e) {

	        System.err.println("Failed to stop campaign: " + e.getMessage());
	        System.err.println("Status code: " + e.getStatusCode());
	        System.err.println("Response body: " + e.getRawBody());
	        e.printStackTrace();
	    
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	public void forceStopSafely(String campaignId) throws IOException {
	    try {
	        ApiClient apiClient = Configuration.getDefaultApiClient();

	        // Step 1: Get the full campaign object
	        String getPath = "/api/v2/outbound/campaigns/" + campaignId;

	        ApiRequest<Void> getRequest = ApiRequestBuilder
	                .create("GET", getPath)
	                .withAccepts("application/json")
	                .withAuthNames("PureCloud OAuth")
	                .build();

	        ApiResponse<String> getResponse = apiClient.invoke(getRequest, new TypeReference<String>() {});
	        String jsonResponse = getResponse.getBody();

	        // Step 2: Modify the state to "off"
	        ObjectMapper mapper = new ObjectMapper();
	        JsonNode campaignJson = mapper.readTree(jsonResponse);
	        if(campaignJson.has("campaignStatus")) {
	        	String campaignStatus = campaignJson.get("campaignStatus").asText();
	        	if(campaignStatus.equalsIgnoreCase("stopping")) {
	        
	        ((ObjectNode) campaignJson).put("campaignStatus", "forced_off"); 

	        String updatedPayload = campaignJson.toString();

	        // Step 3: PUT full campaign object back
	        ApiRequest<String> putRequest = ApiRequestBuilder
	                .create("PUT", getPath)
	                .withBody(updatedPayload)
	                .withContentTypes("application/json")
	                .withAccepts("application/json")
	                .withAuthNames("PureCloud OAuth")
	                .build();

	        apiClient.invoke(putRequest, new TypeReference<Void>() {});
	        System.out.println("Campaign state set to OFF safely for campaignId: " + campaignId);
	        	}
	        }else {
	        	 System.out.println("Campaign status not found" + campaignId);
	        }
	    } catch (ApiException e) {
	        System.err.println("Failed to force stop campaign: " + e.getMessage());
	        System.err.println("Status code: " + e.getStatusCode());
	        System.err.println("Response body: " + e.getRawBody());
	        e.printStackTrace();
	    }
	}
	

	public String getCampaignIdByName(String campaignName) throws IOException {
	    try {
	        OutboundApi api = new OutboundApi();
	        CampaignEntityListing result = api.getOutboundCampaigns(
	            100, 1, "Equals", campaignName,
	            null, null, null, null, null, null, null, null, null
	        );

	        if (result.getEntities() != null && !result.getEntities().isEmpty()) {
	            return result.getEntities().get(0).getId();
	        }

	    } catch (ApiException e) {
	        System.err.println("API error while getting campaign ID: " + e.getMessage());
	    }

	    return null;
	}
	
	public void AppService() {
		PureCloudRegionHosts region = setRegion("us_west_2");
		//////AppController.logger.log("[POINTEL] - AppService() - genesysregion - "+region);
		ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();

		try {
			//getGenesysCredentials();
			String clientId = "70fadae1-7074-4075-a2ed-a5f361668d10";
			String clientSecret = "eqP882PxaUwmLbzzy_x6UDYk3KEoHAifXJOCSFhn284";
			apiClient.authorizeClientCredentials(clientId,clientSecret);
			Configuration.setDefaultApiClient(apiClient);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	private PureCloudRegionHosts setRegion(String region1) {
		PureCloudRegionHosts region = null;
		if (region1.equalsIgnoreCase("ap_northeast_1")) {
			region = PureCloudRegionHosts.ap_northeast_1;
		} else if (region1.equalsIgnoreCase("ap_northeast_2")) {
			region = PureCloudRegionHosts.ap_northeast_2;
		} else if (region1.equalsIgnoreCase("ap_southeast_2")) {
			region = PureCloudRegionHosts.ap_southeast_2;
		} else if (region1.equalsIgnoreCase("ca_central_1")) {
			region = PureCloudRegionHosts.ca_central_1;
		} else if (region1.equalsIgnoreCase("eu_central_1")) {
			region = PureCloudRegionHosts.eu_central_1;
		} else if (region1.equalsIgnoreCase("eu_west_1")) {
			region = PureCloudRegionHosts.eu_west_1;
		} else if (region1.equalsIgnoreCase("eu_west_2")) {
			region = PureCloudRegionHosts.eu_west_2;
		} else if (region1.equalsIgnoreCase("us_east_1")) {
			region = PureCloudRegionHosts.us_east_1;
		} else if (region1.equalsIgnoreCase("us_west_2")) {
			region = PureCloudRegionHosts.us_west_2;
		}
		return region;
	}

}
