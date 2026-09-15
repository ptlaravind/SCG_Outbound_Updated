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
import java.util.Map.Entry;
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

	AppService() {
		try {
			getSecretCredentials();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void getSecretCredentials() {
		try {
			AppController.logger.log("[POINTEL] - getSecretCredentials() - Get getSecret Credentials Process Started");
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
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	public String getLatestBackupData(AmazonS3 s3Client, Map<String, String> resultCodes) {
		String bucketName = System.getenv("backupbucket");
		String prefix = System.getenv("backupfileprefix");
		String[] backupFilePrefix = prefix.split("/");
		String backupFileName = backupFilePrefix[backupFilePrefix.length - 1];

		Pattern pattern = Pattern.compile(backupFileName + "\\d{8}\\.csv");

		ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefix);
		ListObjectsV2Result result = s3Client.listObjectsV2(req);

		String latestFileKey = null;
		LocalDate latestDate = null;
		if (result != null && !result.getObjectSummaries().isEmpty() && result.getObjectSummaries() != null) {
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
				if (content != null && !content.isEmpty()) {
					List<Map<String, String>> dataList = convertCsvToMap(content);
					AppController.logger.log("[POINTEL] - getLatestBackupData() dataList: " + dataList.toString());
					 if(dataList != null && !dataList.isEmpty()) {
	            		 return sendEmailReport(dataList, resultCodes, latestFileKey);
	            	 }else {
	            		 return "Data not found in contact list...";
	            	 }
				}
			} catch (IOException e) {
				AppController.logger.log("[POINTEL] - Error reading file content: " + e.getMessage());
			} catch (Exception error) {
				AppController.logger
						.log("[POINTEL] - Exception occured in getLatestBackupData : " + error.getMessage());
			}
			return "Failed to send email!";
		} else {
			AppController.logger.log("[POINTEL] - getLatestBackupData() - No specified file name found!");
		}
		AppController.logger.log("[POINTEL] - getLatestBackupData() - Content: " + content);

		return "No specified file name found!";
	}

	private String sendEmailReport(List<Map<String, String>> dataList, Map<String, String> resultCodes,
			String latestFileName) throws Exception {
		String response = "";
		try {
			EmailService emailService = new EmailService();
			// List<String> ivr_Collections_Daily_Results = Arrays.asList("INSERTDATE",
			// "CALL_RESULT", "RESULT", "TOTAL");
			List<String> ivr_Collections_Daily_Results = Arrays.asList(System.getenv("dailyResultsColumns").split(","));

			String callRecordLastResultKeyPrefix = System.getenv("callRecordLastResultKeyPrefix");
			if (callRecordLastResultKeyPrefix == null || callRecordLastResultKeyPrefix.isEmpty()) {
				callRecordLastResultKeyPrefix = "CallRecordLastResult";
			}

			String[] split = latestFileName.split("\\.");
			String date = split[0];
			CharSequence subSequence = date.subSequence(date.length() - 8, date.length());
			DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
			LocalDate localDate = LocalDate.parse(subSequence, inputFormatter);
			String formattedDate = localDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

			StringBuilder emailContent = new StringBuilder();
			emailContent.append("<html><body>");
			emailContent.append(
					"<table border='1' style='border-collapse: separate; border-spacing: 2px; width: auto; border: 2px double black;'>");

			emailContent.append("<tr>");
			emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>INSERTDATE</th>");
			emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>CALL_RESULT</th>");
			emailContent.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>RESULT</th>");
			emailContent.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>TOTAL</th>");
			emailContent.append("</tr>");
			Map<String, String> callRecordLastResultCodeMap = getCallRecordResult();
			Map<String, Integer> callResultDataList = getTotalCountByCallResult(dataList, resultCodes,
					callRecordLastResultKeyPrefix, callRecordLastResultCodeMap);
			if (callResultDataList != null && !callResultDataList.isEmpty()) {
				for (Entry<String, Integer> row : callResultDataList.entrySet()) {

					emailContent.append("<tr style='border: 2px double black;'>");

					for (String column : ivr_Collections_Daily_Results) {
						if (column.equals("INSERTDATE")) {
							emailContent.append("<td>").append(formattedDate).append("</td>");
						} else if (column.equals("CALL_RESULT")) {
							String[] callResultDes = row.getKey().split("_");

							String callResult = callResultDes.length > 0 ? callResultDes[0] : "";
							String description = callResultDes.length > 1 ? callResultDes[1] : "";

							int total = row.getValue() != 0 ? row.getValue() : 0;
							emailContent.append("<td>").append(callResult).append("</td>");
							emailContent.append("<td>").append(description).append("</td>");
							emailContent.append("<td>").append(total).append("</td>");
						}
					}
					emailContent.append("</tr>");
				}
			}

			emailContent.append("</table>");
			emailContent.append("</body></html>");

			// e
			// List<String> ivr_collections_wrong_party_columns = Arrays.asList("WRONG
			// PARTY", "ACCTNUMBER", "CUSTOMERNAME","INSERTDATE");
			List<String> ivr_collections_wrong_party_columns = Arrays
					.asList(System.getenv("wrongPartyColumns").split(","));
			StringBuilder wrong_party_emailContent = new StringBuilder();
			wrong_party_emailContent.append("<html><body>");
			wrong_party_emailContent.append(
					"<table border='1' style='border-collapse: separate; border-spacing: 2px; width: auto; border: 2px double black;'>");

			wrong_party_emailContent.append("<tr>");
			wrong_party_emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>WRONG PARTY</th>");
			wrong_party_emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>ACCTNUMBER</th>");
			wrong_party_emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>CUSTOMERNAME</th>");
			wrong_party_emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>INSERTDATE</th>");
			wrong_party_emailContent.append("</tr>");
			String callResult = "CALL_RESULT";
			String wrongPartyColumn = "CONTACT_PHONE";
			if (dataList != null && !dataList.isEmpty()) {
				for (Map<String, String> row : dataList) {
					if (row.get(callResult).equalsIgnoreCase("91") || row.get(callResult).equalsIgnoreCase("40")) {
						wrong_party_emailContent.append("<tr style='border: 2px double black;'>");
						for (String column : ivr_collections_wrong_party_columns) {
							if (column.equals("INSERTDATE")) {
								wrong_party_emailContent.append("<td>").append(formattedDate).append("</td>");
							} else if (column.equals("WRONGPARTY")) {

								wrong_party_emailContent.append("<td>").append(row.get(wrongPartyColumn))
										.append("</td>");
							} else {
								wrong_party_emailContent.append("<td>").append(row.get(column)).append("</td>");
							}
						}
						wrong_party_emailContent.append("</tr>");
					}
					
				}
			}
			wrong_party_emailContent.append("</table>");
			wrong_party_emailContent.append("</body></html>");

			// e

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(
					"This email was automatically generated and was sent from a send-only account; please do not reply to this email.\r\n"
							+ "\r\n"
							+ "Attached please find the daily results for the SCG IVR Collections OBD campaign for Yesterday.\r\n"
							+ "Any Questions please contact SCG CCC Tech Team; CCCTechnology@sempra.com",
					"utf-8");

			DataSource ivr_collections__DailyDataSource = new ByteArrayDataSource(emailContent.toString(), "text/html");

			DataSource ivr_collections_wrong_party_datasource = new ByteArrayDataSource(
					wrong_party_emailContent.toString(), "text/html");

			MimeBodyPart ivr_collections_daily_attachmentpart = new MimeBodyPart();
			ivr_collections_daily_attachmentpart
					.setDataHandler(new jakarta.activation.DataHandler(ivr_collections__DailyDataSource));
			ivr_collections_daily_attachmentpart.setFileName("SCG_IVR_Collections_Daily Results.html");

			MimeBodyPart ivr_collections_wrong_party_attachmentpart = new MimeBodyPart();
			ivr_collections_wrong_party_attachmentpart
					.setDataHandler(new jakarta.activation.DataHandler(ivr_collections_wrong_party_datasource));
			ivr_collections_wrong_party_attachmentpart.setFileName("SCG_IVR_Collections_Wrong_Party_Results.html");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);
			multipart.addBodyPart(ivr_collections_daily_attachmentpart);
			multipart.addBodyPart(ivr_collections_wrong_party_attachmentpart);

			response = emailService.sendEmail("SCG_IVR_Collections_Daily Results", multipart);
			return response;
		} catch (Exception e) {
			AppController.logger.log("[POINTEL] - sendEmailReport() - ()" + Sticky.printStackTrace(e));
			e.printStackTrace();
		}
		response = "Failed to send mail!";
		return response;
	}

	private Map<String, Integer> getTotalCountByCallResult(List<Map<String, String>> dataList,
			Map<String, String> resultCodes, String callRecordLastResultKeyPrefix,
			Map<String, String> callRecordLastResultCodeMap) {

		Map<String, Integer> callResult = null;
		if (dataList != null && !dataList.isEmpty()) {
			callResult = new HashMap<>();
			for (Map<String, String> row : dataList) {
				String column = "CALL_RESULT";

				// boolean isCallResultUpdated = false;
				if (row.get(column) != null && !row.get(column).isEmpty() && !row.get(column).trim().equals("0")) {
					AppController.logger.log("[POINTEL] - processOutboundCsvData() - CALL_RESULT - " + row.get(column));
					// emailContent.append("<td>").append(row.get(column)).append("</td>");
					// emailContent.append("<td>").append(getResultCodeDescription(row.get(column),
					// resultCodes)).append("</td>");
					String resultCode = row.get(column);
					String description = getResultCodeDescription(row.get(column), resultCodes);
					String callResultKey = resultCode + "_" + description;
					if (callResult.containsKey(callResultKey)) {
						int count = callResult.get(callResultKey);
						callResult.put(callResultKey, count + 1);
					} else {
						callResult.put(callResultKey, 1);
					}
					// isCallResultUpdated = true;
				} else {
					AppController.logger.log("[POINTEL] - processOutboundCsvData() - CALL_RESULT not found ");
					String callRecordLastResultCodeKey = getRecordLastResultCode(row, callRecordLastResultKeyPrefix);
					AppController.logger.log(
							"[POINTEL] - processOutboundCsvData() - CallRecordLastResultCodeDesription from cloud -"
									+ callRecordLastResultCodeKey);
					if (callRecordLastResultCodeKey != null && !callRecordLastResultCodeKey.isEmpty()
							&& callRecordLastResultCodeMap != null
							&& callRecordLastResultCodeMap.containsKey(callRecordLastResultCodeKey)) {

						AppController.logger
								.log("[POINTEL] - processOutboundCsvData() - callRecordLastResultCode from config -"
										+ callRecordLastResultCodeMap.get(callRecordLastResultCodeKey));
						String call_resultDescription = getResultCodeDescription(
								callRecordLastResultCodeMap.get(callRecordLastResultCodeKey), resultCodes);
						AppController.logger.log(
								"[POINTEL] - processOutboundCsvData() - callRecordLastResultCodedescription from dynamoDB -"
										+ call_resultDescription);
//						emailContent.append("<td>")
//								.append(callRecordLastResultCodeMap.get(callRecordLastResultCodeKey))
//								.append("</td>");
//						emailContent.append("<td>").append(call_resultDescription).append("</td>");
						// isCallResultUpdated = true;
						String resultCode = callRecordLastResultCodeMap.get(callRecordLastResultCodeKey);
						call_resultDescription = call_resultDescription != null ? call_resultDescription : "";
						resultCode = resultCode != null ? resultCode : "";
						String callResultKey = resultCode + "_" + call_resultDescription;
						if (callResult.containsKey(callResultKey)) {
							int count = callResult.get(callResultKey);
							callResult.put(callResultKey, count + 1);
						} else {
							callResult.put(callResultKey, 1);
						}
					}
				}

			}
		}
		return callResult;
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
			headers[i] = headers[i].replaceAll("^\"|\"$", "").trim(); // Remove surrounding quotes
		}

		for (int i = 1; i < lines.length; i++) {
			//String[] values = lines[i].split(",");
			String[] values = lines[i].split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
			Map<String, String> rowMap = new LinkedHashMap<>();

			for (int j = 0; j < headers.length; j++) {
				String key = headers[j];
				String value = j < values.length ? values[j].replaceAll("^\"|\"$", "").trim() : ""; // Remove quotes
																									// from values too
				rowMap.put(key.toUpperCase(), value);
			}

			result.add(rowMap);
		}

		return result;
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

	public static AmazonS3 getS3Client() {
		return AmazonS3ClientBuilder.standard().withRegion(System.getenv("awsregion")).build();
	}

}
