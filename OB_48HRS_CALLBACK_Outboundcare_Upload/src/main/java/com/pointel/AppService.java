package com.pointel;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
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
			// Region region = Region.of("us-east-1");
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

	public List<Map<String, String>> getOB48HRSCallBackOutboundDetails(String content) {
		AppController.logger.log(
				"[POINTEL] - getOB48HRSCallBackOutboundDetails() - Retrieve 48HRS Call Back Outbound call details from text file Process Started");
		List<Map<String, String>> requestBody = new ArrayList<>();
		String currentLine = "";
		String str = content;
		BufferedReader br = new BufferedReader(new StringReader(str));
		try {
			while ((currentLine = br.readLine()) != null) {
				Map<String, String> detail = new HashMap<>();

				if ((!currentLine.isEmpty()) && (currentLine.length() >= 0)) {

					String account_num = currentLine.substring(0, 10).trim();
					String openDate = currentLine.substring(10, 20).trim();
					String facilitytypecd = currentLine.substring(20, 22).trim();
					String noticetypecd = currentLine.substring(22, 24).trim();
					String cashonlyswitch = currentLine.substring(24, 25).trim();
					String customername = currentLine.substring(25, 70).trim();
					String spousename = currentLine.substring(70, 115).trim();
					String languagecode = currentLine.substring(115, 117).trim();
					String homephone = currentLine.substring(117, 127).trim();
					String workphone = currentLine.substring(127, 147).trim();
					String otherphone = currentLine.substring(147, 157).trim();
					String serviceaddress1 = currentLine.substring(157, 237).trim();
					String serviceaddress2 = currentLine.substring(237, 317).trim();
					String contactname = currentLine.substring(317, 357).trim();
					String mailaddress1 = currentLine.substring(357, 437).trim();
					String mailaddress2 = currentLine.substring(437, 517).trim();
					String mailaddress3 = currentLine.substring(517, 597).trim();
					String section = currentLine.substring(597, 601).trim();
					String segment = currentLine.substring(601, 605).trim();

					// Used the amounts index as 11.
					String noticeamt = currentLine.substring(605, 616).trim();
					String mincollamt = currentLine.substring(616, 627).trim();
					String curbalamt = currentLine.substring(627, 638).trim();
					String deprcvdamt = currentLine.substring(638, 649).trim();
					String rqrddepamt = currentLine.substring(649, 660).trim();
					String prevbalamt = currentLine.substring(660, 671).trim();
					String pymtamt = currentLine.substring(671, 682).trim();
					String pymtbtchdt = currentLine.substring(682, 692).trim();
					String billtotamt = currentLine.substring(692, 703).trim();
					String billenddt = currentLine.substring(703, 713).trim();
					String extnhisttxt = currentLine.substring(713, 725).trim();
					String extnhistntmttxt = currentLine.substring(725, 737).trim();
					String contact_phone = currentLine.substring(737, 747).trim();
					String res_collection_date = "";// currentLine.substring(747, 756).trim();
					String non_res_collection_date = "";// currentLine.substring(756, 765).trim();

					detail.put("0", account_num);
					detail.put("1", openDate);
					detail.put("2", facilitytypecd);
					detail.put("3", noticetypecd);
					detail.put("4", cashonlyswitch);
					detail.put("5", customername);
					detail.put("6", spousename);
					detail.put("7", languagecode);
					detail.put("8", homephone);
					detail.put("9", workphone);
					detail.put("10", otherphone);
					detail.put("11", serviceaddress1);
					detail.put("12", serviceaddress2);
					detail.put("13", contactname);
					detail.put("14", mailaddress1);
					detail.put("15", mailaddress2);
					detail.put("16", mailaddress3);
					detail.put("17", section);
					detail.put("18", segment);
					detail.put("19", convertToDecimal(noticeamt));
					detail.put("20", convertToDecimal(mincollamt));
					detail.put("21", convertToDecimal(curbalamt));
					detail.put("22", convertToDecimal(deprcvdamt));
					detail.put("23", convertToDecimal(rqrddepamt));
					detail.put("24", convertToDecimal(prevbalamt));
					detail.put("25", convertToDecimal(pymtamt));
					detail.put("26", pymtbtchdt);
					detail.put("27", convertToDecimal(billtotamt));
					detail.put("28", billenddt);
					detail.put("29", extnhisttxt);
					detail.put("30", extnhistntmttxt);
					detail.put("31", contact_phone);
					detail.put("32", res_collection_date);
					detail.put("33", non_res_collection_date);
					detail.put("34", "");// CALL_RESULT
					detail.put("35", "0");// CALL_ATTEMPT
					// detail.put("35", filler3); // FILLER

					requestBody.add(detail);
				} else {
					AppController.logger.log(
							"[POINTEL] - getOB48HRSCallBackOutboundDetails() - Current line have not meet the condition: "
									+ currentLine);
				}
			}

			AppController.logger
					.log("[POINTEL] - getOB48HRSCallBackOutboundDetails() - Request body size- " + requestBody.size());
			return requestBody;
		} catch (Exception e) {
			AppController.logger
					.log("[POINTEL] - getOB48HRSCallBackOutboundDetails() - ()" + Sticky.printStackTrace(e));
			e.printStackTrace();
		}
		return requestBody;
	}

	public static String convertToDecimal(String input) {
		try {
			BigDecimal number = new BigDecimal(input);
			BigDecimal decimalAmount = number.movePointLeft(2);
			return decimalAmount.toString();
		} catch (NumberFormatException e) {
			AppController.logger.log("[POINTEL] - convertToDecimal() - input " + input);
			return "";
		}
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
//				"[POINTEL] - uploadCampaignContactList() - Started ");
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

}
