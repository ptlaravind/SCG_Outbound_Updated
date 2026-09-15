package com.pointel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
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
import com.mypurecloud.sdk.v2.api.DownloadsApi;
import com.mypurecloud.sdk.v2.api.OutboundApi;
import com.mypurecloud.sdk.v2.model.CampaignEntityListing;
import com.mypurecloud.sdk.v2.model.ContactList;
import com.mypurecloud.sdk.v2.model.ContactsExportRequest;
import com.mypurecloud.sdk.v2.model.DomainEntityRef;
import com.mypurecloud.sdk.v2.model.ExportUri;
import com.mypurecloud.sdk.v2.model.UrlResponse;
import com.opencsv.CSVReader;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AppService {

	String clientId = "";
	String clientSecret = "";

	AppService() {
		PureCloudRegionHosts region = setRegion(System.getenv("genesysregion"));
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
			// Region region = Region.of("us-east-1");
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
				contactListInfo = contactListId + "|" + contactListName;
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
							+ contactListId + " Process Started");
			Boolean includeImportStatus = false; // Boolean | Import status
			Boolean includeSize = false; // Boolean | Include size
			ContactList columnNamesResult = apiInstance.getOutboundContactlist(contactListId, includeImportStatus,
					includeSize);

			columnNames = columnNamesResult.getColumnNames();
			AppController.logger.log("Column Names Result:" + columnNamesResult.getColumnNames());
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

	public void exportContactList(String contactListId) throws Exception {
		OutboundApi apiInstance = new OutboundApi();
		try {
			ContactsExportRequest body = new ContactsExportRequest(); // ContactsExportRequest | Export information to

			AppController.logger.log("[POINTEL] - exportContactList() - Initiate the export of a contact list -"
					+ contactListId + " Process Started");
			DomainEntityRef exportResult = apiInstance.postOutboundContactlistExport(contactListId, body);
			AppController.logger.log("[POINTEL] - exportContactList() - Export ContactList Result " + exportResult);
		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - exportContactList() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - exportContactList() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}

	public String getDownloadUri(String contactListId) {

		String downloadId = "";
		try {

			PureCloudRegionHosts region = setRegion(System.getenv("genesysregion"));
			ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();

			try {
				apiClient.authorizeClientCredentials(clientId, clientSecret);
				Configuration.setDefaultApiClient(apiClient);
			} catch (IOException | ApiException exception) {
				exception.printStackTrace();
			} catch (Exception exception) {
				exception.printStackTrace();
			}

			OutboundApi apiInstance = new OutboundApi();
			AppController.logger.log("[POINTEL] - getDownloadUri() - Get the Download URI of a contact list -"
					+ contactListId + "  Process Started");
			// ExportUri downloadIdResult =
			// apiInstance.getOutboundContactlistExport(contactListId, "false");
			// String uri = downloadIdResult.getUri().trim();
			ApiResponse<ExportUri> downloadIdResult = apiInstance
					.getOutboundContactlistExportWithHttpInfo(contactListId, "false");
			AppController.logger.log("[POINTEL] - getDownloadUri() result " + downloadIdResult.getRawBody());
			String uri = downloadIdResult.getBody().getUri();

			downloadId = uri.substring(uri.length() - 16);
			AppController.logger.log("[POINTEL] - getDownloadUri() - Contact list's  Download Id -" + downloadId);
			return downloadId;

		} // catch (ApiException apiException) {
			// AppController.logger.log("[POINTEL] - getDownloadUri() - ()" +
			// apiException.getRawBody());
			// apiException.printStackTrace();
			// }
		catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getDownloadUri() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return downloadId;
	}

	public String getDownloadUrl(String downloadId) {
		DownloadsApi downloadApiInstance = new DownloadsApi();
		Boolean issueRedirect = false;
		UrlResponse downloadUrlResult;
		String downloadUrl = "";
		try {
			AppController.logger
					.log("[POINTEL] - getDownloadUrl() - Get the Download Url of the contact list Process Started");
			downloadUrlResult = downloadApiInstance.getDownload(downloadId, null, issueRedirect, null);
			downloadUrl = downloadUrlResult.getUrl();
			return downloadUrl;
		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - getDownloadUrl() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getDownloadUrl() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return downloadUrl;
	}

	public boolean downloadOutboundCsvFile(String downloadUrl, List<String> columnNames, String campaignName,
			Map<String, String> resultCodes, String contactListName) {
		String csvData = "";
		boolean processCsvData = false;
		try {
			URL url = new URL(downloadUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			int responseCode = conn.getResponseCode();
			AppController.logger
					.log("[POINTEL] - downloadOutboundCsvFile() - Get the Outbound Download URL Response code -"
							+ responseCode);

			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				StringBuilder response = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
					response.append("\n");
				}
				in.close();
				csvData = response.toString();
				AppController.logger.log("[POINTEL] - downloadOutboundCsvFile() - Outbound Csv file data  -" + csvData);

				CSVReader csvReader = new CSVReader(new StringReader(csvData));
				processCsvData = processOutboundCsvData(csvReader, columnNames, campaignName, resultCodes);

				createBackupCsvFile(csvData);
				csvReader.close();
			}

			return processCsvData;
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - downloadOutboundCsvFile() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return processCsvData;
	}

	public boolean processOutboundCsvData(CSVReader csvData, List<String> columnNames, String campaignName,
			Map<String, String> resultCodes) {
		 boolean processCsvData = false;
		List<Map<String, String>> csvdataList = new ArrayList<>();
		try {
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - Create text file based csv file Process Started");

			String[] nextRecord = {};

			// Create Current Date Object...................
//			LocalDate currentDate = LocalDate.now();
//			DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
//			String formattedDate = currentDate.format(timeFormatter);
			ZoneId zoneId = ZoneId.of("America/Los_Angeles");
	        ZonedDateTime currentDateTime = ZonedDateTime.now(zoneId);
	        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
	        String formattedDate = currentDateTime.format(timeFormatter);
			AppController.logger.log("[POINTEL] - processOutboundCsvData() - Current Date - " + formattedDate);

			StringBuilder contents = new StringBuilder();

			contents.append("HDR" + formattedDate + "BAD DOG                                                              ");//56 filler 
			contents.append("\n");

			int lineCount = 0;

			String callRecordLastResultKeyPrefix = System.getenv("callRecordLastResultKeyPrefix");
			if (callRecordLastResultKeyPrefix == null || callRecordLastResultKeyPrefix.isEmpty()) {
				callRecordLastResultKeyPrefix = "CallRecordLastResult";
			}
			AppController.logger.log("[POINTEL] - processOutboundCsvData() - callRecordLastResultKeyPrefix -"
					+ callRecordLastResultKeyPrefix);
			Map<String, Integer> columnNameIndex = new HashMap<>();
			while ((nextRecord = csvData.readNext()) != null) {

				if (lineCount == 0) {
					int columnIndex = 0;
					for (String columnName : nextRecord) {
						for (String colName : columnNames) {
							if (colName.equalsIgnoreCase(columnName)) {
								columnNameIndex.put(columnName, columnIndex);
							} // For getting callrecordlostresult.
							else if (columnName.startsWith(callRecordLastResultKeyPrefix)) {
								columnNameIndex.put(columnName, columnIndex);
							}
							// e
						}
						columnIndex++;
					}
				}

				if (lineCount != 0) {
					Map<String, String> detail = new HashMap<>();
					for (String colName : columnNames) {
						int cellCount = 0;
						for (String cell : nextRecord) {
							if (cellCount == columnNameIndex.get(colName)) {
								detail.put(colName, cell);
							}
							// s
							String callresultKey = getRecordLastResultCodeColumn(columnNameIndex,
									callRecordLastResultKeyPrefix);
							if (callresultKey != null && cellCount == columnNameIndex.get(callresultKey)) {
								detail.put(callresultKey, cell);
							}
							// e
							cellCount++;
						}
					}

					csvdataList.add(detail);
				}
				lineCount++;
			}
			Map<String, String> callRecordLastResultCodeMap = getCallRecordResult();
			boolean hasData = false;
			for (Map<String, String> csvDetail : csvdataList) {
				for (String colName : columnNames) {
					if (colName.equalsIgnoreCase("CALL_RESULT")) {
						if (csvDetail.get(colName) != null && !csvDetail.get(colName).isEmpty()
								&& !csvDetail.get(colName).trim().equals("0")) {
							AppController.logger.log(
									"[POINTEL] - processOutboundCsvData() - CALL_RESULT - " + csvDetail.get(colName));
							contents.append(addTrailingSpaces(getResultCodeDescription(csvDetail.get(colName), resultCodes),55));
						} else {
							AppController.logger.log("[POINTEL] - processOutboundCsvData() - CALL_RESULT not found ");
							String callRecordLastResultCodeKey = getRecordLastResultCode(csvDetail,
									callRecordLastResultKeyPrefix);
							AppController.logger.log(
									"[POINTEL] - processOutboundCsvData() - CallRecordLastResultCodeDesription from cloud -"
											+ callRecordLastResultCodeKey);

							if (callRecordLastResultCodeKey != null && !callRecordLastResultCodeKey.isEmpty()
									&& callRecordLastResultCodeMap != null
									&& callRecordLastResultCodeMap.containsKey(callRecordLastResultCodeKey)) {

								AppController.logger.log(
										"[POINTEL] - processOutboundCsvData() - callRecordLastResultCode from config -"
												+ callRecordLastResultCodeMap.get(callRecordLastResultCodeKey));
								String call_resultDescription = getResultCodeDescription(
										callRecordLastResultCodeMap.get(callRecordLastResultCodeKey), resultCodes);
								AppController.logger.log(
										"[POINTEL] - processOutboundCsvData() - callRecordLastResultCodedescription from dynamoDB -"
												+ call_resultDescription);
								contents.append(addTrailingSpaces(call_resultDescription,55));
							}else {
								
								contents.append(addTrailingSpaces("",55));
							}

						}
					} else if (colName.equalsIgnoreCase("OCS_DISP")) {
						contents.append("0");
					} else if (colName.equalsIgnoreCase("Phone_Number")) {
						String phoneNumner = csvDetail.get(colName);
						contents.append(addTrailingSpaces(phoneNumner,10));
					} else if (colName.equalsIgnoreCase("BA_CF_ID")) {
						contents.append(addTrailingSpaces(csvDetail.get(colName),10));
					}else if (colName.equalsIgnoreCase("Random_Number")) {
						contents.append(addTrailingSpaces(csvDetail.get(colName),4));
					}
				}
				contents.append("\n");
				hasData = true;
			}

			lineCount = lineCount - 1;
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - Total phone numbers in text file- " + lineCount);

			String totallines = "0000000000";
			if (lineCount > 0) {
				int digits = String.valueOf(lineCount).length();
				for (int i = 0; i < digits; i++) {
					totallines = totallines.substring(0, totallines.length() - 1);
				}
				totallines += String.valueOf(lineCount);
			}

			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - totalLines in that text file - " + totallines);

			contents.append("TRL" + formattedDate + totallines + "                                                           ");
			if (hasData) {
			AmazonS3 s3Client = getS3Client();

			// Convert content to InputStream
			InputStream inputStream = new ByteArrayInputStream(contents.toString().getBytes());

			String bucketName = System.getenv("sourceBucket");
			String targetBuccket = System.getenv("targetBucket");
			String fileExtension = System.getenv("fileExtension");
			AppController.logger.log("[POINTEL] - processOutboundCsvData() - fileExtension : " + fileExtension);

			//String sourceKey = System.getenv("sourcePath") + "_" + formattedDate + fileExtension;

			//String destinationKey = System.getenv("destinationPath") + "_" + formattedDate + fileExtension;
			
			String sourceKey = System.getenv("sourcePath")  + fileExtension;
			String destinationKey = System.getenv("destinationPath") + "_" + formattedDate + fileExtension;
			// Create a PutObjectRequest
			PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, sourceKey, inputStream, null);

			// Upload the file to S3
			s3Client.putObject(putObjectRequest);
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - Text file created successfully in S3 bucket ");

			CopyObjectRequest copyRequest = new CopyObjectRequest(bucketName, sourceKey, targetBuccket, destinationKey);
			s3Client.copyObject(copyRequest);
			} else {
				AppController.logger.log(
						"[POINTEL] - processOutboundCsvData() - no data found....");
			}
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - Text file copied into Backup folder successfully!!");
			processCsvData = true;
			return processCsvData;

		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - processOutboundCsvData() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();

		}
		return processCsvData;
	}

	public static String getRecordLastResultCode(Map<String, String> map, String prefix) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				return entry.getValue();
			}
		}
		return null;
	}

	public static String getRecordLastResultCodeColumn(Map<String, Integer> map, String prefix) {
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static Map<String, String> getCallRecordResult() {
		String content = "";
		Map<String, String> callRecord = null;
		// s
		AmazonS3 s3Client = getS3Client();

		// e
		String callRecordResultBucketName = System.getenv("callResultConfigBucketName");
		String callRecordResultFilePath = System.getenv("callResultConfigFilePath");

		S3Object s3Object = s3Client.getObject(callRecordResultBucketName, callRecordResultFilePath);
		S3ObjectInputStream inputStream = s3Object.getObjectContent();
		content = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));

		if (content != null && !content.isEmpty()) {
			callRecord = new HashMap<>();
			String currentLine = "";
			String str = content;
			BufferedReader br = new BufferedReader(new StringReader(str));
			try {
				while (((currentLine = br.readLine()) != null) && !(currentLine.isEmpty())
						&& (currentLine.length() > 0)) {

					String[] callResults = currentLine.split(":");

					if (callResults.length > 1) {
						callRecord.put(callResults[0].trim(), callResults[1].trim());
					}

				}
				AppController.logger.log(
						"[POINTEL] - getCallRecordResult() - callRecordLastResultCodeMap - " + callRecord.toString());
			} catch (Exception e) {
				AppController.logger.log("[POINTEL] - getCallRecordResult() - ()" + Sticky.printStackTrace(e));
				e.printStackTrace();
			}

		}

		return callRecord;
	}

	public static AmazonS3 getS3Client() {
		return AmazonS3ClientBuilder.standard().withRegion(System.getenv("awsregion")).build();
	}

	public static String getResultCodeDescription(String resultCode, Map<String, String> resultCodes) {
		String resultCodeDesc = "";
		try {
			resultCodeDesc = resultCodes.get(resultCode);
			if (resultCodeDesc == null)
				resultCodeDesc = "";
			return resultCodeDesc;
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getResultCodeDescription() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return resultCodeDesc;
	}

	public void createBackupCsvFile(String csvData) {
		try {
			AppController.logger.log("[POINTEL] - Create a csv file from response Process Started");
			// s
			AmazonS3 s3Client = getS3Client();
			// e
//			LocalDate currentDate = LocalDate.now();
//			DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
//			String formattedDate = currentDate.format(timeFormatter);
			ZoneId zoneId = ZoneId.of("America/Los_Angeles");
	        ZonedDateTime currentDateTime = ZonedDateTime.now(zoneId);
	        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
	        String formattedDate = currentDateTime.format(timeFormatter);

			String targetBuccket = System.getenv("targetBucket");

			String s3FilePath = System.getenv("destinationPath") + "_" + formattedDate + ".csv";

			InputStream inputStream = new ByteArrayInputStream(csvData.getBytes());
			// Create a PutObjectRequest
			PutObjectRequest putObjectRequest = new PutObjectRequest(targetBuccket, s3FilePath, inputStream, null);

			// Upload the file to S3
			s3Client.putObject(putObjectRequest);
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - CSV file copied into Backup folder successfully!!");
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - createBackupCsvFile() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
	}

	public void clearConactList(String contactListId) throws Exception, ApiException {
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
	
	public static String addTrailingSpaces(String input, int count) {

		int countToAdd = count - input.length();
		StringBuilder sb = new StringBuilder(input);
		if (countToAdd >= 1) {
			for (int i = 0; i < countToAdd; i++) {
				sb.append(' ');
			}
		}
		return sb.toString();
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
