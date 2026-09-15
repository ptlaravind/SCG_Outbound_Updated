package com.pointel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
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

	String clientId = "";
	String clientSecret = "";
	public static String email = "";
	public static String password = "";
	public static String secretKey = "";

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
			email = secretJson.get("email").asText();
			password = secretJson.get("password").asText();
			secretKey = secretJson.get("SecretKey").asText();
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
			ApiResponse<ExportUri> downloadIdResult = apiInstance
					.getOutboundContactlistExportWithHttpInfo(contactListId, "false");
			AppController.logger.log("[POINTEL] - getDownloadUri() result " + downloadIdResult.getRawBody());
			String uri = downloadIdResult.getBody().getUri();

			downloadId = uri.substring(uri.length() - 16);
			AppController.logger.log("[POINTEL] - getDownloadUri() - Contact list's  Download Id -" + downloadId);
			return downloadId;

		} 
		catch (Exception exception) {
			AppController.logger.log("[POINTEL] - getDownloadUri() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return downloadId;
	}

	public List<Map<String, String>> downloadOutboundCsvFile(String downloadUrl, List<String> columnNames,
			String campaignName, String contactListName) {
		String csvData = "";
		List<Map<String, String>> processCsvData = null;
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
				processCsvData = processOutboundCsvData(csvReader, columnNames, campaignName);

				csvReader.close();
			}

			return processCsvData;
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - downloadOutboundCsvFile() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return processCsvData;
	}

	public List<Map<String, String>> processOutboundCsvData(CSVReader csvData, List<String> columnNames,
			String campaignName) {
		List<Map<String, String>> csvdataList = new ArrayList<>();
		try {
			AppController.logger
					.log("[POINTEL] - processOutboundCsvData() - Create text file based csv file Process Started");

			String[] nextRecord = {};


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


			return csvdataList;

		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - processOutboundCsvData() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();

		}
		return csvdataList;
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

	public String sendEmailReport(List<Map<String, String>> dataList, Map<String, String> resultCodes)
			throws Exception {
		String response = "";
		try {
			EmailService emailService = new EmailService();
			// List<String> ivr_Collections_Daily_Results =
			// Arrays.asList("CALL_DATE,CALL_RESULT,TOTAL");
			List<String> ivr_Collections_Daily_Results = Arrays.asList(System.getenv("reportColumns").split(","));

			String callRecordLastResultKeyPrefix = System.getenv("callRecordLastResultKeyPrefix");
			if (callRecordLastResultKeyPrefix == null || callRecordLastResultKeyPrefix.isEmpty()) {
				callRecordLastResultKeyPrefix = "CallRecordLastResult";
			}

			StringBuilder emailContent = new StringBuilder();
			emailContent.append("<html><body>");
			emailContent.append(
					"<table border='1' style='border-collapse: separate; border-spacing: 2px; width: auto; border: 2px double black;'>");

			emailContent.append("<tr>");
			emailContent
					.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>CALL_DATE</th>");
			emailContent.append(
					"<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>CALL_RESULT</th>");
			emailContent.append("<th style='border: 2px double black; padding: 5px; white-space: nowrap;'>TOTAL</th>");
			emailContent.append("</tr>");
			Map<String, String> callRecordLastResultCodeMap = getCallRecordResult();
			Map<String, Integer> callResultDataList = getTotalCountByCallResult(dataList, resultCodes,
					callRecordLastResultKeyPrefix, callRecordLastResultCodeMap);

			if(callResultDataList != null && !callResultDataList.isEmpty()) {
			for (Entry<String, Integer> row : callResultDataList.entrySet()) {
				emailContent.append("<tr style='border: 2px double black;'>");
				for (String column : ivr_Collections_Daily_Results) {
					if (column.equals("CALL_DATE")) {

						String[] callResultDes = row.getKey().split("_");
						String callDate = callResultDes[0];
				        LocalDate date = LocalDate.parse(callDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

				        String formattedDate = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
						emailContent.append("<td>").append(formattedDate).append("</td>");

					} else if (column.equals("CALL_RESULT")) {
						String[] callResultDes = row.getKey().split("_");
						String description = callResultDes.length > 2 ? callResultDes[2] : "";

						int total = row.getValue() != 0 ? row.getValue() : 0;
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

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText("Attached is the daily summary.  Please do not reply to this automated message.");

			DataSource ivr_collections__DailyDataSource = new ByteArrayDataSource(emailContent.toString(), "text/html");

			MimeBodyPart ivr_collections_daily_attachmentpart = new MimeBodyPart();
			ivr_collections_daily_attachmentpart
					.setDataHandler(new jakarta.activation.DataHandler(ivr_collections__DailyDataSource));
			ivr_collections_daily_attachmentpart.setFileName("Seasonal Daily Summary Report.html");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);
			multipart.addBodyPart(ivr_collections_daily_attachmentpart);
			int year = Calendar.getInstance().get(Calendar.YEAR);
			response = emailService.sendEmail(year + " Seasonal Light Outbound Campaign", multipart);
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
			callResult = new LinkedHashMap<>();
			for (Map<String, String> row : dataList) {
				String column = "CALL_RESULT";
				String uploadDateColumn = "uploadDate";

				if (row.get(column) != null && !row.get(column).isEmpty() && !row.get(column).trim().equals("0")) {
					AppController.logger.log("[POINTEL] - processOutboundCsvData() - CALL_RESULT - " + row.get(column));

					String resultCode = row.get(column);
					String uploadDate = row.get(uploadDateColumn);
					String description = getResultCodeDescription(row.get(column), resultCodes);
					String callResultKey = uploadDate+ "_" +resultCode + "_" + description;
					if (callResult.containsKey(callResultKey)) {
						int count = callResult.get(callResultKey);
						callResult.put(callResultKey, count + 1);
					} else {
						callResult.put(callResultKey, 1);
					}
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
						String resultCode = callRecordLastResultCodeMap.get(callRecordLastResultCodeKey);
						call_resultDescription = call_resultDescription != null ? call_resultDescription : "";
						resultCode = resultCode != null ? resultCode : "";
						String uploadDate = row.get(uploadDateColumn);
						String callResultKey = uploadDate+ "_" +resultCode + "_" + call_resultDescription;
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
			String[] values = lines[i].split(",");
			Map<String, String> rowMap = new LinkedHashMap<>();

			for (int j = 0; j < headers.length; j++) {
				String key = headers[j];
				String value = j < values.length ? values[j].replaceAll("^\"|\"$", "").trim() : ""; // Remove quotes
																									// from values too
				rowMap.put(key, value);
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

	public void saveOutboundDataToDnamoDB(List<Map<String, String>> outboundDetails) {
		try {
			String awsRegion = System.getenv("awsregion");
			String tableName = System.getenv("tableName");

			AppController.logger.log("[POINTEL] - saveResultCode() - DynamoDb Region: " + awsRegion);
			AppController.logger.log("[POINTEL] - saveResultCode() - DynamoDb Table Name: " + tableName);

			Regions region = Regions.valueOf(awsRegion.replace("-", "_").toUpperCase());

			AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
			int index = 1;
			ObjectMapper objectMapper = new ObjectMapper();
			for (Map<String, String> outboundDetail : outboundDetails) {
				LocalDateTime currentDate = LocalDateTime.now();
				DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");
				String formattedDate = currentDate.format(timeFormatter);
				
				DateTimeFormatter uploadTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
				String uploadFormattedDate = currentDate.format(uploadTimeFormatter);
				String jsonData = objectMapper.writeValueAsString(outboundDetail);
				Map<String, AttributeValue> item = new HashMap<>();

				item.put("documentId", new AttributeValue(formattedDate + "_" + index));
				// item.put("data", new AttributeValue(outboundDetail.toString()));
				item.put("data", new AttributeValue(jsonData));
				item.put("uploadDate", new AttributeValue(uploadFormattedDate));

				AppController.logger.log("[POINTEL] - saveResultCode() - item: " + item.toString());
				PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);

				amazonDynamoDB.putItem(putItemRequest);
				index++;
			}
		} catch (Exception exception) {
			AppController.logger.log("[POINTEL] - saveResultCode() - Error occurred: " + exception.getMessage());
			exception.printStackTrace();
		}
	}

	public void clearConactList(String contactListId) throws Exception {
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

	public List<Map<String, String>> getDataList() {
		String awsRegion = System.getenv("awsregion");
		String tableName = System.getenv("tableName");
		Regions region = Regions.valueOf(awsRegion.replace("-", "_").toUpperCase());

		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
		DynamoDB dynamoDB = new DynamoDB(client);
		Table table = dynamoDB.getTable(tableName);

		// Step 1: Read all data from the table
		List<Item> items = new ArrayList<>();
		ScanSpec scanSpec = new ScanSpec();
		for (Item item : table.scan(scanSpec)) {
			items.add(item);
		}
		//items.sort(Comparator.comparingInt(item -> extractIndexFromDocumentId(item.getString("uploadDate"))));
//		items.sort(Comparator
//			    .comparing((Item item) -> extractDateTimeFromDocumentId(item.getString("documentId")))
//			    .thenComparingInt(item -> extractIndexFromDocumentId(item.getString("documentId"))));
		items.sort(Comparator.comparingInt(item -> Integer.parseInt(item.getString("uploadDate"))));
		AppController.logger.log("[POINTEL] - items @@@ - ()" + items.toString());
		return convertToMap(items);
	}

	

	public List<Map<String, String>> convertToMap(List<Item> items) {
		List<Map<String, String>> dataList = null;
		ObjectMapper objectMapper = null;
		Map<String, String> dataMap = null;
		try {
			if (items != null && !items.isEmpty()) {
				objectMapper = new ObjectMapper();
				dataList = new ArrayList<>();
				dataMap = new LinkedHashMap<>();
				for (Item item : items) {

					// if (attributes.containsKey("data")) {
					if (item.hasAttribute("data")) {
						String jsonString = item.getString("data").trim();
						dataMap = objectMapper.readValue(jsonString, new TypeReference<Map<String, String>>() {
						});

						if (item.hasAttribute("uploadDate")) {
							
							dataMap.put("uploadDate", item.getString("uploadDate"));
						}

					} else {
						AppController.logger.log("convertToMap() Unexpected data format in DynamoDB!");
					}
					dataList.add(dataMap);
				}
			}
			return dataList;
		} catch (Exception e) {
			AppController.logger.log("Exception occured convertToMap() " + Sticky.printStackTrace(e));
		}
		return dataList;
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
