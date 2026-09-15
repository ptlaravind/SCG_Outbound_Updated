package com.pointel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.pointel.model.ResultCode;

public class AppController implements RequestHandler<String, String> {

	public static LambdaLogger logger;

	@Override
	public String handleRequest(String input, Context context) {
		try {
			logger = context.getLogger();
			AppService service = new AppService();
			logger.log("[POINTEL] - handleRequest() - Download Outbound CSV file  Process Started");

			Map<String, String> resultCodes = loadResultCodeProperties();

			String campaignName = System.getenv("campaignName");

			logger.log("[POINTEL] - handleRequest() - campaignName : " + campaignName);
			String contactListId = service.getContactListId(campaignName);
			if (contactListId != null && !contactListId.isEmpty()) {
				List<String> columnNames = service.getContactListColumnNames(contactListId);

				service.exportContactList(contactListId);
//				// Added sleep
//				int waitTimeMs = 5000; // 5 seconds
//
//				Thread.sleep(waitTimeMs);
//				// end
//				String downloadId = service.getDownloadUri(contactListId);
				
				//s
				String downloadId = null;
				int maxAttempts = 10;
				int attempt = 0;
				downloadId = service.getDownloadUri(contactListId);
				while ((downloadId == null || downloadId.isEmpty()) && attempt < maxAttempts) {
				    attempt++;
				    //service.exportContactList(contactListId);
				    logger.log("[POINTEL] - handleRequest() - get download id attempt : " + attempt);
				    Thread.sleep(30000); // Wait 30 seconds per attempt
				    
				    downloadId = service.getDownloadUri(contactListId);
				}
				//e

				if (downloadId != null && (!downloadId.isEmpty())) {
					String downloadUrl = service.getDownloadUrl(downloadId);
					String downloadOutboundFlag = service.downloadOutboundCsvFile(downloadUrl, columnNames,
							campaignName, resultCodes);
					if (downloadOutboundFlag.equalsIgnoreCase("true")) {
						String campaignId = service.getCampaignIdByName(campaignName);
						service.forceStopSafely(campaignId);
						 //service.clearConactList(contactListId); It should be clear at end of month.
						return "OutboundCare text file created successfully!!";
					} else {
						return "Failed to create outboundCare text file!!!";
					}
				}
			} else {
				return "Invalid Campaign or ContactList not found!!";
			}
		} catch (Exception e) {
			e.printStackTrace();
			AppController.logger.log("[POINTEL] - exportContactList() - ()" + Sticky.printStackTrace(e));
		}
		return "Failed to create outboundCare text file!!!";

	}

	private Map<String, String> loadResultCodeProperties() {

		logger.log("[POINTEL] - loadResultCodeProperties() - Load result code and its description process started");
		Map<String, String> resultCodesKvp = new HashMap<>();
		try {
			String tableName = System.getenv("resultCodeTableName");
			logger.log("[POINTEL] - loadResultCodeProperties() - DynamoDbTableNametableName:" + tableName);

			Regions region = Regions.valueOf(System.getenv("awsregion").replace("-", "_").toUpperCase());
			logger.log("[POINTEL] - loadResultCodeProperties() - DynamoDb Region:" + System.getenv("awsregion"));

			AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withRegion(region).build();
			DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
					.withTableNameOverride(DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(tableName))
					.build();

			DynamoDBMapper mapper = new DynamoDBMapper(amazonDynamoDB, mapperConfig);
			DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();

			List<ResultCode> scanResult = mapper.scan(ResultCode.class, scanExpression);
			for (ResultCode result : scanResult) {
				resultCodesKvp.put(result.getResultCode(), result.getCodeDescription());
			}
			logger.log("[POINTEL] - loadResultCodeProperties() - resultCOde details:" + resultCodesKvp.toString());
			return resultCodesKvp;
		} catch (Exception exception) {
			logger.log("[POINTEL] - loadResultCodeProperties() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
		}
		return resultCodesKvp;
	}
}
