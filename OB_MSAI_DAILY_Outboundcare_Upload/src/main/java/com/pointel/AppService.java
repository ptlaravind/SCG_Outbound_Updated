package com.pointel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
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
			//String secretName = "SCG_Outbound_Secrets";
			//Region region = Region.of("us-east-1");
			String secretName = System.getenv("secretname");
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

	public List<Map<String, String>> getMSAIDailyOutboundDetails(String content) {
		AppController.logger.log(
				"[POINTEL] - getMSAIDailyOutboundDetails() - Retrieve MSAI Daily Outbound call details from text file Process Started");
		List<Map<String, String>> requestBody = new ArrayList<>();
		String currentLine = "";
		String str = content;
		BufferedReader br = new BufferedReader(new StringReader(str));
		try {
			while ((currentLine = br.readLine()) != null) {
				Map<String, String> detail = new HashMap<>();

				if ((!currentLine.isEmpty()) && (currentLine.length() >= 0)) {

					String gnn_Id = currentLine.substring(0, 10).trim();
					String msai_Id = currentLine.substring(10, 15).trim();
					String even_cr_ts = currentLine.substring(15, 41).trim();
					String area_code = currentLine.substring(41, 44).trim();
					String phone = currentLine.substring(41, 51).trim();
					String language_cd = currentLine.substring(51, 53).trim();

					detail.put("0", gnn_Id);// GNN-ID
					detail.put("1", msai_Id);// MSAI-ID
					detail.put("2", even_cr_ts);// EVNT-CR-TS
					detail.put("3", area_code);// AREA-CODE
					detail.put("4", phone);// PHONE-NUM
					detail.put("5", language_cd);// LANGUAGE_CD
					detail.put("6", "");// CALL_RESULT
					requestBody.add(detail);
				} else {
					AppController.logger.log(
							"[POINTEL] - getMSAIDailyOutboundDetails() - Current line have not meet the condition: "
									+ currentLine);
				}
			}

			AppController.logger
					.log("[POINTEL] - getMSAIDailyOutboundDetails() - Request body size- " + requestBody.size());
			return requestBody;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - getMSAIDailyOutboundDetails() - ()" + Sticky.printStackTrace(e));
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
//				"[POINTEL] - uploadCampaignContactList() - Upload call ahead ContactList details into Genesys Process Started ");
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

//	public String getLatestBackupData(AmazonS3 s3Client) {
//		// Define your bucket and prefix
//		String bucketName = System.getenv("backupbucket");
//		String prefix = System.getenv("backupfileprefix");
//		String[] backupFilePrefix = prefix.split("/");
//		String backupFileName = backupFilePrefix[backupFilePrefix.length-1];
//
//		// Pattern to match files like "MSAI_Daily_20240101.txt"
//		Pattern pattern = Pattern.compile(backupFileName+"\\d{8}\\.txt");
//
//		// List objects with the specified prefix
//		ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefix);
//		ListObjectsV2Result result = s3Client.listObjectsV2(req);
//		String content = "";
//		for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
//			String key = objectSummary.getKey();
//			String fileName = key.substring(key.lastIndexOf("/") + 1); // Get the file name only
//
//			// Check if the file name matches the pattern
//			if (pattern.matcher(fileName).matches()) {
//				AppController.logger.log("[POINTEL] - getLatestBackupData() - Latest file founded : " + fileName);
//				String[] filenameArr = fileName.split("_");
//				String updatedDate = filenameArr[filenameArr.length-1];
//				AppController.logger.log("[POINTEL] - getLatestBackupData() - Latest filekey : " + prefix+updatedDate);
//				S3Object s3Object = s3Client.getObject(bucketName, prefix+updatedDate);
//				S3ObjectInputStream inputStream = s3Object.getObjectContent();
//				content = new BufferedReader(new InputStreamReader(inputStream)).lines()
//						.collect(Collectors.joining("\n"));
//				break;
//			}
//
//		}
//		//Check only the no answer.
//		AppController.logger.log("[POINTEL] - getLatestBackupData() - content : " + content);
//		if(!content.isEmpty()) {
//			
//			
//		}
//		return content;
//		
//	}
	
//	public String getLatestBackupData(AmazonS3 s3Client) {
//	    // Define your bucket and prefix
//	    String bucketName = System.getenv("backupbucket");
//	    String prefix = System.getenv("backupfileprefix");
//	    String[] backupFilePrefix = prefix.split("/");
//	    String backupFileName = backupFilePrefix[backupFilePrefix.length - 1];
//
//	    // Pattern to match files like "MSAI_Daily_20240101.txt"
//	    Pattern pattern = Pattern.compile(backupFileName + "\\d{8}\\.csv");
//
//	    // List objects with the specified prefix
//	    ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefix);
//	    ListObjectsV2Result result = s3Client.listObjectsV2(req);
//
//	    String latestFileKey = null;
//	    LocalDate latestDate = null;
//	    if(result != null && !result.getObjectSummaries().isEmpty() && result.getObjectSummaries() != null) {
//	    for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
//	        String key = objectSummary.getKey();
//	        String fileName = key.substring(key.lastIndexOf("/") + 1); // Get the file name only
//	        // Check if the file name matches the pattern
//	        if (pattern.matcher(fileName).matches()) {
//	            // Extract date from the filename
//	            String dateStr = fileName.substring(fileName.lastIndexOf("_") + 1, fileName.lastIndexOf(".csv"));
//	            try {
//	                LocalDate fileDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
//
//	                // Compare to find the latest date
//	                if (latestDate == null || fileDate.isAfter(latestDate)) {
//	                    latestDate = fileDate;
//	                    latestFileKey = key;
//	                }
//	            } catch (DateTimeParseException e) {
//	                AppController.logger.log("[POINTEL] - Invalid date format in file: " + fileName);
//	            }
//	        }
//	    }
//	}
//
//	    // If a file with the latest date is found, fetch its content
//	    String content = "";
//	    String formattedContent = "";
//	    if (latestFileKey != null) {
//	        AppController.logger.log("[POINTEL] - getLatestBackupData() - Latest filekey: " + latestFileKey);
//	        S3Object s3Object = s3Client.getObject(bucketName, latestFileKey);
//	        try (S3ObjectInputStream inputStream = s3Object.getObjectContent();
//	             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
//	            content = reader.lines().collect(Collectors.joining("\n"));
//	            if(content != null && !content.isEmpty()) {
//	            	List<Map<String, String>> resultMapList = convertCsvToMap(content);
//	            	//List<String> columns = Arrays.asList("MSAIOBD_GNN_ID", "MSAIOBD_MSAI_ID", "MSAIOBD_EVNT_CR_TS", "MSAIOBD_AREA_CODE", "MSAIOBD_PHONE_NUM", "MSAIOBD_LANG_CODE");
//	            	//List<String> callRecordLatResults = Arrays.asList("ININ-OUTBOUND-NUMBER_COULD_NOT_BE_DIALED", "ININ-OUTBOUND-NOT-CALLABLE-TIME");
//	            	
//	            	List<String> columns = Arrays.asList(System.getenv("columns").split(","));
//	            	List<String> callRecordLatResults = Arrays.asList(System.getenv("callResultDesToFilter").split(","));
//	            	
//	            	formattedContent = appendOrderedValuesToContent(resultMapList, columns, callRecordLatResults);
//	            	
//	            }
//	        } catch (IOException e) {
//	            AppController.logger.log("[POINTEL] - Error reading file content: " + e.getMessage());
//	        }
//	    }
//
//	    AppController.logger.log("[POINTEL] - getLatestBackupData() - Content: " + content);
//	    return formattedContent;
//	}
	
    public static String appendOrderedValuesToContent(List<Map<String, String>> data, 
    		List<String> columns, List<String> callRecordLatResults) {
        // Read the original content

        // Process each map in the list
    	//List<String> call_resultToCheck = Arrays.asList("0","1","2");
    	String call_resultkey = "CALL_RESULT";
    	String callRecordLastResultKeyPrefix = "CallRecordLastResult";
        String content = "";
		if (data != null && !data.isEmpty()) {
			for (Map<String, String> row : data) {
				String callRecordLastResult = getRecordLastResultCode(row, callRecordLastResultKeyPrefix);

//        	if(row.get(call_resultkey) != null && !row.get(call_resultkey).isEmpty() && call_resultToCheck.contains(row.get(call_resultkey))
//        			|| (row.get(call_resultkey) == null || row.get(call_resultkey).isEmpty()) && 
//        			(callRecordLastResult != null && callRecordLatResults.contains(callRecordLastResult)))
				if ((row.get(call_resultkey) == null || row.get(call_resultkey).isEmpty()
						|| row.get(call_resultkey).equals("0"))
						&& (callRecordLastResult != null && callRecordLatResults.contains(callRecordLastResult))) {

					// Collect values based on the column order
					List<String> orderedValues = new ArrayList<>();
					for (String column : columns) {
						orderedValues.add(row.getOrDefault(column, "")); // Use default "" if key is missing
					}

					// Append the ordered values to the content
					String appendedRow = String.join("", orderedValues);
					content += "\n" + appendedRow;
				}
			}
		}

        return content;
    }
    
    public static String getRecordLastResultCode(Map<String, String> map, String prefix) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (entry.getKey().startsWith(prefix)) {
				return entry.getValue();
			}
		}
		return null;
	}
	
	
	public static List<Map<String, String>> convertCsvToMap(String content) {
        List<Map<String, String>> result = new ArrayList<>();

        // Split the content into lines
        String[] lines = content.split("\n");

        // Ensure we have at least a header and one row
        if (lines.length < 2) {
            //throw new IllegalArgumentException("Content must have at least one header row and one data row.");
        	return result;
        }

        // Extract headers (keys)
        String[] headers = lines[0].split(",");

        // Iterate over the data rows
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(",");

            // Map each value to its corresponding header
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                String key = headers[j].trim();
                String value = j < values.length ? values[j].trim() : ""; // Handle missing values
                rowMap.put(key, value);
            }

            // Add the row map to the result
            result.add(rowMap);
        }

        return result;
    }

	public String insertContactListToS3(String content) {
		AppController.logger.log("[POINTEL] - insertContactListToS3() - started");
	    String baseFileName = System.getenv("fileDestination");
	    String bucket = System.getenv("backupbucket");


	    int batchSize = 300;
	    int maxFiles = 5;
	    int fileCount = 0;
	    int lineCount = 0;

	    List<String> currentBatch = new ArrayList<>();
	    List<String> first300Records = new ArrayList<>();
	    List<String> remaining = new ArrayList<>();

	    try (BufferedReader br = new BufferedReader(new StringReader(content))) {
	        String line;
	        while ((line = br.readLine()) != null) {
	            line = line.trim();
	            if (!line.isEmpty()) {
	                if (fileCount < maxFiles) {
	                    currentBatch.add(line);
	                    if (first300Records.size() < 300) {
	                        first300Records.add(line);
	                    }
	                    lineCount++;
	                    if (lineCount == batchSize) {
	                        fileCount++;
	                        uploadToS3(bucket, baseFileName + fileCount + ".raw", String.join("\n", currentBatch));
	                        currentBatch.clear();
	                        lineCount = 0;
	                    }
	                } else {
	                    remaining.add(line);
	                }
	            }
	        }

	        // Upload leftover lines from currentBatch (if < 300 and under file 5)
	        if (!currentBatch.isEmpty() && fileCount < maxFiles) {
	            fileCount++;
	            uploadToS3(bucket, baseFileName + fileCount + ".raw", String.join("\n", currentBatch));
	        }

	        // Upload any remaining lines to file 6
	        if (!remaining.isEmpty()) {
	            uploadToS3(bucket, baseFileName + "6" + ".raw", String.join("\n", remaining));
	        }

	    } catch (Exception e) {
	    	 AppController.logger.log("[POINTEL] - Error occured while insertContactListToS3: " + e.getMessage());
	        return null;
	    }

	    return String.join("\n", first300Records);
	}

	private void uploadToS3(String bucket, String key, String content) {
		try {
		AmazonS3 s3Client = getS3Client();
		
		InputStream inputStream = new ByteArrayInputStream(content.getBytes());
		// Create a PutObjectRequest
		PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, key, inputStream, null);

		// Upload the file to S3
		s3Client.putObject(putObjectRequest);
		}catch (Exception e) {
			 AppController.logger.log("[POINTEL] - Error occured while uploadToS3: " + e.getMessage());
		}
	}
	
	public static AmazonS3 getS3Client() {
		return AmazonS3ClientBuilder.standard().withRegion(System.getenv("awsregion")).build();
	}

}


