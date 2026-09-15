package com.pointel;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.fasterxml.jackson.core.type.TypeReference;
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
			// String secretName = "SCG_Outbound_Secrets";
			String secretName = System.getenv("secretname");
			Region region = Region.of(System.getenv("awsregion"));
			// Region region = Region.of("us-east-1");
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
			AppController.logger.log("[POINTEL] - getContactListId() - ApiException-" + exception.getMessage());
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

	public List<Map<String, String>> getSeasonalLightOutboundDetails(String content) {
		AppController.logger.log(
				"[POINTEL] - getSeasonalLightOutboundDetails() - Retrieve Seasonal Ahead Outbound call details from text file Process Started");
		List<Map<String, String>> requestBody = new ArrayList<>();
		String currentLine = "";
		String str = content;
		BufferedReader br = new BufferedReader(new StringReader(str));
		try {
			while ((currentLine = br.readLine()) != null) {
				Map<String, String> detail = new HashMap<>();

				if ((!currentLine.isEmpty()) && (currentLine.length() >= 0)) {
					String[] currentLineData = currentLine.split(";");

					detail.put("0", currentLineData.length > 0 ? currentLineData[0] : "");// BA_ID
					detail.put("1", currentLineData.length > 1 ? currentLineData[1] : "");// NAME
					detail.put("2", currentLineData.length > 2 ? currentLineData[2] : "");// SERV_ADDRESS
					detail.put("3", currentLineData.length > 3 ? currentLineData[3] : "");// SERV_CITY
					detail.put("4", currentLineData.length > 4 ? currentLineData[4] : "");// SERV_ZIP
					detail.put("5", currentLineData.length > 5 ? currentLineData[5] : "");// MAIL_ADDRESS
					detail.put("6", currentLineData.length > 6 ? currentLineData[6] : "");// MAIL_CITY
					detail.put("7", currentLineData.length > 7 ? currentLineData[7] : "");// MAIL_STATE
					detail.put("8", currentLineData.length > 8 ? currentLineData[8] : "");// MAIL_ZIP
					detail.put("9", currentLineData.length > 9 ? currentLineData[9] : "");// MAIL_OVRFLW
					detail.put("10", currentLineData.length > 10 ? currentLineData[10] : "");// ORDER_TYPE
					detail.put("11", currentLineData.length > 11 ? currentLineData[11] : "");// ORDER_DATE
					detail.put("12", currentLineData.length > 12 ? currentLineData[12] : "");// PHONE
					detail.put("13", currentLineData.length > 13 ? currentLineData[13] : "");// BASE
					detail.put("14", "");// CALL_STATUS
					detail.put("15", "");// CALL_RESULT
					// detail.put("14", currentLineData[14].trim());// EMAIL_ADDRESS
					requestBody.add(detail);
				} else {
					AppController.logger.log(
							"[POINTEL] - getSeasonalLightOutboundDetails() - Current line have not meet the condition: "
									+ currentLine);
				}
			}

			AppController.logger
					.log("[POINTEL] - getSeasonalLightOutboundDetails() - Request body size- " + requestBody.size());
			if (requestBody.size() > 1) {
				requestBody.remove(0);// removed the header.
			}
			return requestBody;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - getSeasonalLightOutboundDetails() - ()" + Sticky.printStackTrace(e));
			e.printStackTrace();
		}
		return requestBody;
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
//	public void uploadCampaignContactList(List<String> columnNames, String contactListId,
//			List<Map<String, String>> outboundDetails) {
//		AppController.logger.log(
//				"[POINTEL] - uploadCampaignContactList() - Upload ContactList details into Genesys Process Started ");
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
//				int columnSize = columnNames.size();
//				int fieldIndex = 0;
//				for (String columnName : columnNames) {
//					if (fieldIndex <= columnSize) {
//						data.put(columnName, outBoundDetail.get(String.valueOf(fieldIndex)));
//						fieldIndex++;
//					}
//				}
//				contactObj.setData(data);
//				contactObj.setCallable(true);
//				body.add(contactObj);
//			}
//			AppController.logger.log(
//					"[POINTEL] - uploadCampaignContactList() - Upload ContactList RequestBody- " + body.toString());
//
//			// Add contacts to a contact list.
//			List<DialerContact> result = apiInstance.postOutboundContactlistContacts(contactListId, body, null, null,
//					null);
//			AppController.logger
//					.log("[POINTEL] - uploadCampaignContactList() - Upload ContactList Response- " + result);
//		} catch (ApiException exception) {
//			AppController.logger
//					.log("[POINTEL] - uploadCampaignContactList() - ApiException-" + exception.getMessage());
//			AppController.logger.log("[POINTEL] - uploadCampaignContactList() - RawBody- " + exception.getRawBody());
//			AppController.logger
//					.log("[POINTEL] - uploadCampaignContactList() - ()" + Sticky.printStackTrace(exception));
//			exception.printStackTrace();
//		} catch (Exception exception) {
//			AppController.logger
//					.log("[POINTEL] - uploadSeasonalCampaignContactList() - ()" + Sticky.printStackTrace(exception));
//			exception.printStackTrace();
//		}
//	}

	public void saveOutboundData(List<Map<String, String>> outboundDetails) {
		try {
			String awsRegion = System.getenv("awsregion");
			String tableName = System.getenv("uploadTableName");

			AppController.logger.log("[POINTEL] - saveOutboundData() - DynamoDb Region: " + awsRegion);
			AppController.logger.log("[POINTEL] - saveOutboundData() - DynamoDb Table Name: " + tableName);

			Regions region = Regions.valueOf(awsRegion.replace("-", "_").toUpperCase());

			AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
			int index = 1;
			ObjectMapper objectMapper = new ObjectMapper();
			for (Map<String, String> outboundDetail : outboundDetails) {
				LocalDateTime currentDate = LocalDateTime.now();
				DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
				String formattedDate = currentDate.format(timeFormatter);

				String jsonData = objectMapper.writeValueAsString(outboundDetail);
				Map<String, AttributeValue> item = new HashMap<>();

				item.put("documentId", new AttributeValue(formattedDate + "_" + index));
				// item.put("data", new AttributeValue(outboundDetail.toString()));
				item.put("data", new AttributeValue(jsonData));
				item.put("uploadDate", new AttributeValue(formattedDate));
				item.put("uploadStatus", new AttributeValue().withBOOL(false));

				AppController.logger.log("[POINTEL] - saveOutboundData() - item: " + item.toString());
				PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);

				amazonDynamoDB.putItem(putItemRequest);
				index++;
			}
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - saveOutboundData() - Error occurred: " + exception.getMessage());
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

	/**
	 * Extracts the numeric index from documentId (e.g., "20250402 10:07:56_3" → 3)
	 */
	private int extractIndexFromDocumentId(String documentId) {
		try {
			String[] parts = documentId.split("_");
			return Integer.parseInt(parts[1]);
		} catch (Exception e) {
			AppController.logger.log(
					"[POINTEL] - extractIndexFromDocumentId() Error extracting index from documentId: " + documentId);
			return 0;
		}
	}

	public List<Map<String, String>> getUploadData() {
		String awsRegion = System.getenv("awsregion");
		String tableName = System.getenv("uploadTableName");
		Regions region = Regions.valueOf(awsRegion.replace("-", "_").toUpperCase());

		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
		DynamoDB dynamoDB = new DynamoDB(client);
		Table table = dynamoDB.getTable(tableName);

		List<Item> items = new ArrayList<>();
		ScanSpec scanSpec = new ScanSpec();
		for (Item item : table.scan(scanSpec)) {
			items.add(item);
		}

		if (items.isEmpty()) {
			AppController.logger.log("[POINTEL] -  getUploadData() - No records found.");
			return null;
		}

		items.sort(Comparator.comparingInt(item -> extractIndexFromDocumentId(item.getString("documentId"))));

		int totalSize = items.size();
		int numRuns = 21;

		int baseSize = totalSize / numRuns; // Minimum items per run
		int remainder = totalSize % numRuns; // Extra items to distribute

		List<List<Item>> partitions = new ArrayList<>();
		int index = 0;

		for (int i = 0; i < numRuns && index < totalSize; i++) {
			int runSize = baseSize + (i < remainder ? 1 : 0); // Extra item for first 'remainder' runs
			partitions.add(items.subList(index, Math.min(index + runSize, totalSize)));
			index += runSize;
		}

		int selectedPartitionIndex = -1;
		for (int i = 0; i < partitions.size(); i++) {
			List<Item> partition = partitions.get(i);
			boolean isCompleted = partition.stream().allMatch(item -> "true".equals(item.getString("uploadStatus")));

			if (!isCompleted) {
				selectedPartitionIndex = i;
				break;
			}
		}

		if (selectedPartitionIndex == -1) {
			AppController.logger.log("[POINTEL] -  getUploadData() - All parts are already processed.");
			return null;
		}

		List<Item> selectedPartition = partitions.get(selectedPartitionIndex);
		AppController.logger.log("[POINTEL] -  getUploadData() - Selected partition " + selectedPartition.toString());

		if (selectedPartition != null && !selectedPartition.isEmpty()) {
			for (Item item : selectedPartition) {
				String documentId = item.getString("documentId");

				Map<String, AttributeValue> key = new HashMap<>();
				key.put("documentId", new AttributeValue().withS(documentId));

				Map<String, AttributeValue> updateValues = new HashMap<>();
				updateValues.put(":status", new AttributeValue().withS("true"));

				UpdateItemRequest updateRequest = new UpdateItemRequest().withTableName(tableName).withKey(key)
						.withUpdateExpression("SET uploadStatus = :status").withExpressionAttributeValues(updateValues)
						.withReturnValues(ReturnValue.UPDATED_NEW);

				client.updateItem(updateRequest);
				AppController.logger.log("[POINTEL] -  getUploadData() - Updated item : " + documentId);
			}
		}

		return convertToMap(selectedPartition);
	}

	public List<Map<String, String>> convertToMap(List<Item> items) {
		List<Map<String, String>> dataList = null;
		ObjectMapper objectMapper = null;
		Map<String, String> dataMap = null;
		try {
			if (items != null && !items.isEmpty()) {
				objectMapper = new ObjectMapper();
				dataList = new ArrayList<>();
				dataMap = new HashMap<>();
				for (Item item : items) {
					Map<String, Object> attributes = item.asMap();

					if (attributes.containsKey("data")) {
						String jsonString = item.getString("data").trim();
						dataMap = objectMapper.readValue(jsonString, new TypeReference<Map<String, String>>() {
						});
					} else {
						AppController.logger.log("[POINTEL] - convertToMap() Unexpected data format in DynamoDB!");
					}
					dataList.add(dataMap);
				}
			}
			return dataList;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - convertToMap() Exception occured  " + Sticky.printStackTrace(e));
		}
		return dataList;
	}

}
