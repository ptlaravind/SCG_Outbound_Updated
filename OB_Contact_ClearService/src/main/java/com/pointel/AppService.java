package com.pointel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
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
import com.mypurecloud.sdk.v2.model.ContactList;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AppService {

	String clientId = "";
	String clientSecret = "";

	AppService() {
		PureCloudRegionHosts region = setRegion(System.getenv("genesysregion"));
		AppController.logger.log("[POINTEL] - AppService() - genesysregion - " + region);
		ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();

		try {
			getGenesysCredentials();
			apiClient.authorizeClientCredentials(clientId, clientSecret);
			Configuration.setDefaultApiClient(apiClient);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void getGenesysCredentials() {
		try {
			AppController.logger.log("[POINTEL] - getGenesysCredentials() - Get Genesys Credentials Process Started");
			String secretName = System.getenv("secretname");
			//Region region = Region.of("us-east-1");
			Region region = Region.of(System.getenv("awsregion"));
			SecretsManagerClient client = SecretsManagerClient.builder().region(region).build();
			GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
			GetSecretValueResponse getSecretValueResponse;
			getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
			String secretString = getSecretValueResponse.secretString();

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode secretJson = objectMapper.readTree(secretString);
			clientId = secretJson.get("clientId").asText();
			clientSecret = secretJson.get("clientSecret").asText();

		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	public String getContactListInfo(String campaignName) {
		OutboundApi apiInstance = new OutboundApi();
		String contactListInfo = "";
		try {
			AppController.logger
					.log("[POINTEL] - getContactListInfo() - Get Campaign Id and ContactListId Process Started");
			Integer pageSize = 100;
			Integer pageNumber = 1;
			String filterType = "Equals";
			CampaignEntityListing result = apiInstance.getOutboundCampaigns(pageSize, pageNumber, filterType,
					campaignName, null, null, null, null, null, null, null, null, null);
			if (!result.getEntities().isEmpty() && result.getEntities().get(0).getContactList() != null) {
				
				String contactListId = result.getEntities().get(0).getContactList().getId();
				String contactListName = result.getEntities().get(0).getContactList().getName();
				contactListInfo = contactListId+"|"+contactListName;
				AppController.logger.log("[POINTEL] - getContactListInfo() - contactListName " + contactListName);
			}
			AppController.logger.log("[POINTEL] - getContactListInfo() - ContactList info- " + contactListInfo);
			return contactListInfo;
		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - getContactListInfo() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return contactListInfo;
	}

	public List<String> getContactListColumnNames(String contactListId) {
		OutboundApi apiInstance = new OutboundApi();
		List<String> columnNames = new ArrayList<>();
		try {
			AppController.logger
					.log("[POINTEL] - getContactListColumnNames() - Get the Columns Name of a contact list -"
							+ contactListId + "  Process Started");
			Boolean includeImportStatus = false; // Boolean | Import status
			Boolean includeSize = false; // Boolean | Include size
			ContactList columnNamesResult = apiInstance.getOutboundContactlist(contactListId, includeImportStatus,
					includeSize);

			columnNames = columnNamesResult.getColumnNames();
			AppController.logger.log("[POINTEL] - getContactListColumnNames() Column Names Result:" + columnNamesResult.getColumnNames());
			AppController.logger
					.log("[POINTEL] - getContactListColumnNames() -  Columns Name of the contact list -" + columnNames);
			return columnNames;

		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - getContactListColumnNames() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			AppController.logger
					.log("[POINTEL] - getContactListColumnNames() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return columnNames;
	}


	public void clearConactList(String contactListId) {
		try {
			OutboundApi apiInstance = new OutboundApi();
			AppController.logger.log("[POINTEL] - clearConactList() - Clear the contents of a contact list -"
					+ contactListId + "  Process Started");
			apiInstance.postOutboundContactlistClear(contactListId);
			AppController.logger.log("[POINTEL] - clearConactList() - ContactList cleared Successfully");
		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - clearConactList() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - clearConactList() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}
	
	public void forceStopSafely(String campaignId) throws IOException {
	    try {
	        ApiClient apiClient = Configuration.getDefaultApiClient();

	        String getPath = "/api/v2/outbound/campaigns/" + campaignId;

	        ApiRequest<Void> getRequest = ApiRequestBuilder
	                .create("GET", getPath)
	                .withAccepts("application/json")
	                .withAuthNames("PureCloud OAuth")
	                .build();

	        ApiResponse<String> getResponse = apiClient.invoke(getRequest, new TypeReference<String>() {});
	        String jsonResponse = getResponse.getBody();

	        ObjectMapper mapper = new ObjectMapper();
	        JsonNode campaignJson = mapper.readTree(jsonResponse);
	        if(campaignJson.has("campaignStatus")) {
	        	String campaignStatus = campaignJson.get("campaignStatus").asText();
	        	AppController.logger.log("[POINTEL] - forceStopSafely() campaignStatus: " + campaignStatus);
	        	if(campaignStatus.equalsIgnoreCase("stopping")) {
	        
	        ((ObjectNode) campaignJson).put("campaignStatus", "forced_off"); 

	        String updatedPayload = campaignJson.toString();

	        ApiRequest<String> putRequest = ApiRequestBuilder
	                .create("PUT", getPath)
	                .withBody(updatedPayload)
	                .withContentTypes("application/json")
	                .withAccepts("application/json")
	                .withAuthNames("PureCloud OAuth")
	                .build();

	        apiClient.invoke(putRequest, new TypeReference<Void>() {});
	        AppController.logger.log("[POINTEL] - forceStopSafely() Campaign state set to forced off safely for campaignId: " + campaignId);
	        	}
	        }else {
	        	AppController.logger.log("[POINTEL] - forceStopSafely() Campaign status not found" + campaignId);
	        }
	    } catch (ApiException e) {
	    	AppController.logger.log("[POINTEL] - forceStopSafely() Failed to force stop campaign: " + e.getMessage());
	    	AppController.logger.log("[POINTEL] - forceStopSafely() Status code: " + e.getStatusCode());
	    	AppController.logger.log("[POINTEL] - forceStopSafely() Response body: " + e.getRawBody());
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
	    	AppController.logger.log("[POINTEL] - getCampaignIdByName() API error while getting campaign ID: " + e.getMessage());
	    }

	    return null;
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
