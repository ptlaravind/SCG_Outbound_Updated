package com.pointel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;
import com.mypurecloud.sdk.v2.api.OutboundApi;
import com.mypurecloud.sdk.v2.model.CampaignEntityListing;

import jakarta.activation.DataSource;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

public class AppService {

	public static String email = "";
	public static String password = "";
	public static String secretKey = "";
	public static String clientId = "";
	public static String clientSecret = "";
	
	AppService() {
		PureCloudRegionHosts region = setRegion(System.getenv("genesysregion"));
		ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();
		try {
			getSecretCredentials();
			apiClient.authorizeClientCredentials(clientId, clientSecret);
			Configuration.setDefaultApiClient(apiClient);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void getSecretCredentials() {
		try {
			AppController.logger.log("[POINTEL] - getSecretCredentials() - getSecret Credentials Process Started");
			String secretName = System.getenv("secretname");
			Region region = Region.of(System.getenv("awsregion"));
			SecretsManagerClient client = SecretsManagerClient.builder().region(region).build();
			GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build();
			GetSecretValueResponse getSecretValueResponse;
			getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
			String secretString = getSecretValueResponse.secretString();

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode secretJson = objectMapper.readTree(secretString);
			email = secretJson.get("email").asText();
			password = secretJson.get("password").asText();
			secretKey = secretJson.get("SecretKey").asText();
			clientId = secretJson.get("clientId").asText();
			clientSecret = secretJson.get("clientSecret").asText();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	public String getContactListName(String campaignName) {
		OutboundApi apiInstance = new OutboundApi();
		String contactListName = "";
		try {
			AppController.logger
					.log("[POINTEL] - getContactListInfo() - Get Campaign Id and ContactListId Process Started");
			Integer pageSize = 100;
			Integer pageNumber = 1;
			String filterType = "Equals";
			CampaignEntityListing result = apiInstance.getOutboundCampaigns(pageSize, pageNumber, filterType,
					campaignName, null, null, null, null, null, null, null, null, null);
			if (!result.getEntities().isEmpty() && result.getEntities().get(0).getContactList() != null) {
				contactListName = result.getEntities().get(0).getContactList().getName();
				AppController.logger.log("[POINTEL] - getContactListInfo() - contactListName " + contactListName);
			}
			return contactListName;
		} catch (ApiException apiException) {
			AppController.logger.log("[POINTEL] - getContactListInfo() - ()" + apiException.getRawBody());
			apiException.printStackTrace();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		return contactListName;
	}


	public String getLatestBackupData(AmazonS3 s3Client,String contactListName, Map<String, String> resultCodes) {
	    // Define your bucket and prefix
	    String bucketName = System.getenv("backupbucket");
	    String prefix = System.getenv("backupfileprefix");
	    String[] backupFilePrefix = prefix.split("/");
	    String backupFileName = backupFilePrefix[backupFilePrefix.length - 1];

	    // Pattern to match files like "MSAI_Daily_20240101.txt"
	    Pattern pattern = Pattern.compile(backupFileName + "\\d{8}\\.csv");

	    // List objects with the specified prefix
	    ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefix);
	    ListObjectsV2Result result = s3Client.listObjectsV2(req);

	    String latestFileKey = null;
	    LocalDate latestDate = null;
	    if(result != null && !result.getObjectSummaries().isEmpty() && result.getObjectSummaries() != null) {
	    for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
	        String key = objectSummary.getKey();
	        String fileName = key.substring(key.lastIndexOf("/") + 1); // Get the file name only
	        if (pattern.matcher(fileName).matches()) {
	            String dateStr = fileName.substring(fileName.lastIndexOf("_") + 1, fileName.lastIndexOf(".csv"));
	            try {
	                LocalDate fileDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
	                if (latestDate == null || fileDate.isAfter(latestDate)) {
	                    latestDate = fileDate;
	                    latestFileKey = key;
	                }
	            } catch (DateTimeParseException e) {
	                AppController.logger.log("[POINTEL] - Invalid date format in file: " + fileName);
	            }
	        }
	    }
	}

	    // If a file with the latest date is found, fetch its content
	    String content = "";
	    if (latestFileKey != null) {
	        AppController.logger.log("[POINTEL] - getLatestBackupData() - Latest filekey: " + latestFileKey);
	        S3Object s3Object = s3Client.getObject(bucketName, latestFileKey);
	        try (S3ObjectInputStream inputStream = s3Object.getObjectContent();
	             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
	            content = reader.lines().collect(Collectors.joining("\n"));
	            if(content != null && !content.isEmpty()) {
	            	List<Map<String, String>> dataList = convertCsvToMap(content);
	            	 AppController.logger.log("[POINTEL] - getLatestBackupData() dataList: " + dataList.toString());
	            	 if(dataList != null && !dataList.isEmpty()) {
	            		 return sendEmailReport(dataList, contactListName, resultCodes, latestFileKey);
	            	 }else {
	            		 return "Data not found in contact list...";
	            	 }
	            	 
	            }
	        } catch (IOException e) {
	            AppController.logger.log("[POINTEL] - Error reading file content: " + e.getMessage());
	        }catch (Exception error) {
	            AppController.logger.log("[POINTEL] - Exception occured in getLatestBackupData : " + error.getMessage());
	        }
	        return "Failed to send email!";
	    }else {
	    	 AppController.logger.log("[POINTEL] - getLatestBackupData() - No specified file name found!");
	    }
	    AppController.logger.log("[POINTEL] - getLatestBackupData() - Content: " + content);
	   
	    return "No specified file name found!";
	}


	private String sendEmailReport(List<Map<String, String>> dataList, String contactListName, Map<String, String> resultCodes, String latestFileName) throws Exception {
		String response = "";
		try {
			EmailService emailService = new EmailService();
			//List<String> columns = Arrays.asList("INSERT_DATE", "LISTNAME", "Language_Code", "CALL_RESULT","Phone_Number");// Need to update in ENV
			List<String> columns = Arrays.asList(System.getenv("emailReportColumns").split(","));
					
			String callRecordLastResultKeyPrefix = System.getenv("callRecordLastResultKeyPrefix");
			if (callRecordLastResultKeyPrefix == null || callRecordLastResultKeyPrefix.isEmpty()) {
				callRecordLastResultKeyPrefix = "CallRecordLastResult";
			}
			//final String callRecordLastResultConst = callRecordLastResultKeyPrefix;

			StringBuilder emailContent = new StringBuilder();
			emailContent.append("<html><body>");
			emailContent.append(
					"<table border='1' style='border-collapse: separate; border-spacing: 2px; width: auto; border: 2px double black;'>");

			emailContent.append("<tr>");
			emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>INSERT_DATE</th>");
			emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>LISTNAME</th>");
			emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>LANG_CODE</th>");
			emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>CALL_RESULT</th>");
			emailContent.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>PHONE1</th>");
			emailContent.append("</tr>");
			if (!dataList.isEmpty() && dataList != null) {
				Map<String, String> callRecordLastResultCodeMap = getCallRecordResult();
				for (Map<String, String> row : dataList) {
					emailContent.append("<tr style='border: 2px double black;'>");
					for (String column : columns) {
						if (column.equals("INSERT_DATE")) {
							String[] split = latestFileName.split("\\.");
							String date = split[0];
							CharSequence subSequence = date.subSequence(date.length()-8, date.length());
							DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
					        LocalDate localDate = LocalDate.parse(subSequence, inputFormatter);
					        String formattedDate = localDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + " 12:15:00 PM";
							emailContent.append("<td>").append(formattedDate).append("</td>");
						} else if (column.equals("LISTNAME")) {
							emailContent.append("<td>").append(contactListName).append("</td>");
						} else if (column.equals("CALL_RESULT")) {
							boolean isCallResultUpdated = false;
							if (row.get(column) != null && !row.get(column).isEmpty()
									&& !row.get(column).trim().equals("0")) {
								AppController.logger.log(
										"[POINTEL] - processOutboundCsvData() - CALL_RESULT - " + row.get(column));
								emailContent.append("<td>").append(getResultCodeDescription(row.get(column), resultCodes)).append("</td>");
								isCallResultUpdated = true;
							} else {
								AppController.logger.log("[POINTEL] - processOutboundCsvData() - CALL_RESULT not found ");
								String callRecordLastResultCodeKey = getRecordLastResultCode(row,
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
									emailContent.append("<td>").append(call_resultDescription).append("</td>");
									isCallResultUpdated = true;
								}
							}
							if(!isCallResultUpdated) {
								emailContent.append("<td>").append("null").append("</td>");
							}
						
						} else {
							emailContent.append("<td>").append(row.get(column)).append("</td>");
						}
					}
					emailContent.append("</tr>");
				}
			}

			emailContent.append("</table>");
			emailContent.append("</body></html>");

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(
					"Attached please find the daily OBD CARE_RECERT Summary Report for yesterday.  Please do not reply to this automated message.",
					"utf-8");

			DataSource dataSource = new ByteArrayDataSource(emailContent.toString(), "text/html");

			MimeBodyPart attachmentPart = new MimeBodyPart();
			attachmentPart.setDataHandler(new jakarta.activation.DataHandler(dataSource));
			attachmentPart.setFileName("DailyCareRecertSummaryReport.html");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);
			multipart.addBodyPart(attachmentPart);

			response = emailService.sendEmail("SCG Daily CARE RECERT Summary Report GCX", multipart);
			return response;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - sendEmailReport() - ()" + Sticky.printStackTrace(e));
			e.printStackTrace();
		}
		response = "Failed to send mail!";
		return response; 
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
	
	public List<Map<String, String>> convertCsvToMap(String content) {
	    List<Map<String, String>> result = new ArrayList<>();
	    String[] lines = content.split("\n");

	    if (lines.length < 2) {
	        return result;
	    }

	    // Trim headers and remove quotes if present
	    String[] headers = lines[0].replace("\uFEFF", "").trim().split(",");

	    for (int i = 0; i < headers.length; i++) {
	        headers[i] = headers[i].replaceAll("^\"|\"$", "").trim();  // Remove surrounding quotes
	    }

	    for (int i = 1; i < lines.length; i++) {
	        String[] values = lines[i].split(",");
	        Map<String, String> rowMap = new LinkedHashMap<>();

	        for (int j = 0; j < headers.length; j++) {
	            String key = headers[j];
	            String value = j < values.length ? values[j].replaceAll("^\"|\"$", "").trim() : ""; // Remove quotes from values too
	            rowMap.put(key.toUpperCase(), value);
	        }

	        result.add(rowMap);
	    }

	    return result;
	}

//	public List<Map<String, String>> convertCsvToMap(String content) {
//        List<Map<String, String>> result = new ArrayList<>();
//
//        String[] lines = content.split("\n");
//
//        if (lines.length < 2) {
//        	return result;
//        }
//
//        String[] headers = lines[0].split(",");
//
//        for (int i = 1; i < lines.length; i++) {
//            String[] values = lines[i].split(",");
//
//            Map<String, String> rowMap = new LinkedHashMap<>();
//            for (int j = 0; j < headers.length; j++) {
//                //String key = headers[j].trim();
//            	String key = headers[j].replace("\uFEFF", "").trim().intern();
//                String value = j < values.length ? values[j].trim() : ""; // Handle missing values
//                rowMap.put(key, value);
//            }
//
//            result.add(rowMap);
//        }
//
//        return result;
//    }


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


	public static AmazonS3 getS3Client() {
		return AmazonS3ClientBuilder.standard().withRegion(System.getenv("awsregion")).build();
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
