package com.pointel;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;
import com.mypurecloud.sdk.v2.api.OutboundApi;
import com.mypurecloud.sdk.v2.model.CampaignEntityListing;
import com.mypurecloud.sdk.v2.model.ContactList;
import com.mypurecloud.sdk.v2.model.DialerContact;
import com.mypurecloud.sdk.v2.model.WritableDialerContact;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AppService {

	String clientId = "";
	String clientSecret = "";

	AppService() {
		PureCloudRegionHosts region = setRegion(System.getenv("genesysregion"));
		AppController.logger.log("[POINTEL] - AppService() - genesysregion - "+region);
		ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();

		try {
			getGenesysCredentials();
			apiClient.authorizeClientCredentials(clientId,clientSecret);
			Configuration.setDefaultApiClient(apiClient);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void getGenesysCredentials() {
		try {
			AppController.logger.log("[POINTEL] - getGenesysCredentials() - Get Genesys Credentials Process Started");
			String secretName = System.getenv("secretname");
			AppController.logger.log("[POINTEL] - getGenesysCredentials() - secretName "+secretName);
			//Region region = Region.of("us-west-2");
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
			AppController.logger.log("[POINTEL] - getGenesysCredentials() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}

	public List<Map<String, String>> getOutboundDetails(String content) {
		AppController.logger.log(
				"[POINTEL] - getOutboundDetails() - Retrieve Outbound call details from text file Process Started");
		List<Map<String, String>> requestBody = new ArrayList<>();
		String currentLine = "";
		String str = content;
		BufferedReader br = new BufferedReader(new StringReader(str));
		int lineCount = 0;
		String campaignName = "";
		try {
			while (((currentLine = br.readLine()) != null) && !(currentLine.isEmpty()) && (currentLine.length() > 0)) {
				Map<String, String> detail = new HashMap<>();

				// to add empty spaces for line which has lenght less than 24.
				if (currentLine.length() < 24) {
					int remainingLength = 24 - currentLine.length();
					for (int i = 0; i < remainingLength; i++) {
						currentLine = currentLine + " ";
					}
					AppController.logger
							.log("[POINTEL] - getOutboundDetails() - current add space- linecount" + lineCount);
				}

				if (lineCount == 0) {
					lineCount++;
					String data = currentLine.trim();
					campaignName = currentLine.substring(11, data.length());
				}

				if (lineCount == 2) {
					lineCount++;
					detail.put("6", campaignName);
				}
				lineCount++;

				String accountNumber = currentLine.substring(0, 10).trim();
				String randomNumber = currentLine.substring(10, 14).trim();
				String phoneNumber = currentLine.substring(14, 24).trim();

				detail.put("0", accountNumber);
				detail.put("1", randomNumber);
				detail.put("2", phoneNumber);
				detail.put("3", "");//OCS_DISP
				detail.put("4", "");//LanguageCode
				detail.put("5", "");//CALL_RESULT

				requestBody.add(detail);
			}

			AppController.logger.log("[POINTEL] - getOutboundDetails() - Request body size- " + requestBody.size());
			if (requestBody.size() > 2) {
				requestBody.remove(0);//remove the header.
				requestBody.remove(requestBody.size() - 1);//remove the TRL
			}
			return requestBody;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - getOutboundDetails() - ()" + Sticky.printStackTrace(e));
			e.printStackTrace();
		}
		return requestBody;
	}

	public String getContactListId(String campaignName) {
		AppController.logger.log("[POINTEL] - getContactListId() - Get ContactList Id from Genesys Process Started ");
		String contactListId = "";
		try {
			OutboundApi apiInstance = new OutboundApi();
			Integer pageSize = 100; // Integer | Page size. The max that will be returned is 100.
			Integer pageNumber = 1; // Integer | Page number
			String filterType = "Equals"; // String | Filter type

			CampaignEntityListing result = apiInstance.getOutboundCampaigns(pageSize, pageNumber, filterType,
					campaignName, null, null, null, null, null, null, null, null, null);
			AppController.logger.log("[POINTEL] - getContactListId() -Campaign name :" + campaignName);
			if (!result.getEntities().isEmpty() && result.getEntities().get(0).getContactList() != null) {
				contactListId = result.getEntities().get(0).getContactList().getId();
			}
			AppController.logger.log("[POINTEL] - getContactListId() - ContactList Id- " + contactListId);
		} catch (ApiException exception) {
			AppController.logger.log("[POINTEL] - getContactListId() - ApiException- " + exception.getMessage());
			AppController.logger.log("[POINTEL] - getContactListId() - RawBody- " + exception.getRawBody());
			AppController.logger.log("[POINTEL] - getContactListId() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getContactListId() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}

		return contactListId;
	}

	public List<String> getColumnNames(String contactListId) {
		AppController.logger.log("[POINTEL] - getColumnNames() - Get Column Names from Genesys Process Started ");
		List<String> columnNames = new ArrayList<>();
		OutboundApi apiInstance = new OutboundApi();

		Boolean includeImportStatus = false;
		Boolean includeSize = false;
		ContactList result;
		try {
			result = apiInstance.getOutboundContactlist(contactListId, includeImportStatus, includeSize);
			columnNames = result.getColumnNames();
			AppController.logger.log("[POINTEL] - getColumnNames() - Column Names from Genesys- " + columnNames);
			return columnNames;
		} catch (ApiException exception) {
			AppController.logger.log("[POINTEL] - getColumnNames() - ApiException-" + exception.getMessage());
			AppController.logger.log("[POINTEL] - getColumnNames() - RawBody- " + exception.getRawBody());
			AppController.logger.log("[POINTEL] - getColumnNames() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getColumnNames() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return columnNames;
	}

	public void uploadCampaignContactList(List<String> columnNames, String contactListId,
			List<Map<String, String>> outboundDetails) {
		AppController.logger.log("[POINTEL] - uploadCampaignContactList() - Started ");
		OutboundApi apiInstance = new OutboundApi();
		ObjectMapper mapper = new ObjectMapper();

		final int MAX_BATCH_SIZE_BYTES = 1400000; // 1.5 MB safe limit
		final int MAX_BATCH_RECORDS = 1000;

		List<WritableDialerContact> currentBatch = new ArrayList<>();
		int currentBatchSize = 0;

		try {
			for (Map<String, String> outBoundDetail : outboundDetails) {
				WritableDialerContact contactObj = new WritableDialerContact();
				contactObj.setContactListId(contactListId);
				contactObj.setCallable(true);

				Map<String, String> data = new HashMap<>();
				int fieldIndex = 0;
				for (String columnName : columnNames) {
					data.put(columnName, outBoundDetail.get(String.valueOf(fieldIndex)));
					fieldIndex++;
				}
				contactObj.setData(data);

				int contactSize = mapper.writeValueAsBytes(contactObj).length;

				boolean exceedsByteLimit = (currentBatchSize + contactSize > MAX_BATCH_SIZE_BYTES);
				boolean exceedsRecordLimit = (currentBatch.size() >= MAX_BATCH_RECORDS);

				if (exceedsByteLimit || exceedsRecordLimit) {
// Send current batch
					sendBatch(apiInstance, contactListId, currentBatch);
					currentBatch = new ArrayList<>();
					currentBatchSize = 0;
				}

				currentBatch.add(contactObj);
				currentBatchSize += contactSize;
			}

// Send the remaining batch
			if (!currentBatch.isEmpty()) {
				sendBatch(apiInstance, contactListId, currentBatch);
			}

		} catch (ApiException exception) {
			AppController.logger
					.log("[POINTEL] - uploadCampaignContactList() - ApiException-" + exception.getMessage());
			AppController.logger.log("[POINTEL] - uploadCampaignContactList() - RawBody- " + exception.getRawBody());
			AppController.logger
					.log("[POINTEL] - uploadCampaignContactList() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		} catch (Exception exception) {
			AppController.logger
					.log("[POINTEL] - uploadCampaignContactList() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}

	private void sendBatch(OutboundApi apiInstance, String contactListId, List<WritableDialerContact> batch)
			throws ApiException {
		try {
			AppController.logger.log("[POINTEL] - sendBatch() - Uploading batch of size: " + batch.size());
			List<DialerContact> result = apiInstance.postOutboundContactlistContacts(contactListId, batch, null, null,
					null);
			AppController.logger.log("[POINTEL] - sendBatch() - Upload ContactList Response- " + result);
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - sendBatch() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}
	
//	public void uploadContactList(List<String> columnNames, String contactListId,
//			List<Map<String, String>> outboundDetails) {
//		AppController.logger
//				.log("[POINTEL] - uploadContactList() - Upload ContactList details into Genesys Process Started ");
//		OutboundApi apiInstance = new OutboundApi();
//		List<WritableDialerContact> body = new ArrayList<>();
//
//		try {
//			for (int i = 0; i < outboundDetails.size(); i++) {
//				WritableDialerContact contactObj = new WritableDialerContact();
//				contactObj.setContactListId(contactListId);
//
//				Map<String, String> outBoundDetail = outboundDetails.get(i);
//
//				Map<String, String> data = new HashMap<>();
//				data.put(columnNames.get(0), outBoundDetail.get("0"));
//				data.put(columnNames.get(1), outBoundDetail.get("1"));
//				data.put(columnNames.get(2), outBoundDetail.get("2"));
//				data.put(columnNames.get(3), outBoundDetail.get("3"));
//				data.put(columnNames.get(4), outBoundDetail.get("4"));
//				data.put(columnNames.get(5), outBoundDetail.get("5"));
//				contactObj.setData(data);
//				contactObj.setCallable(true);
//
//				body.add(contactObj);
//			}
//
//			// Add contacts to a contact list.
//			List<DialerContact> result = apiInstance.postOutboundContactlistContacts(contactListId, body, null, null,
//					null);
//			AppController.logger.log("[POINTEL] - uploadContactList() - Upload ContactList Response- " + result);
//		} catch (ApiException exception) {
//			AppController.logger.log("[POINTEL] - uploadContactList() - ApiException-" + exception.getMessage());
//			AppController.logger.log("[POINTEL] - uploadContactList() - RawBody- " + exception.getRawBody());
//			AppController.logger.log("[POINTEL] - uploadContactList() - ()" + Sticky.printStackTrace(exception));
//			exception.printStackTrace();
//		} catch (Exception exception) {
//			AppController.logger.log("[POINTEL] - uploadContactList() - ()" + Sticky.printStackTrace(exception));
//			exception.printStackTrace();
//		}
//	}

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
