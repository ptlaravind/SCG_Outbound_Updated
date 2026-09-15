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
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.pointel.model.ResultCode;

public class AppController implements RequestHandler<String, String> {
	
	private AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
	public static LambdaLogger logger;

	@Override
	public String handleRequest(String input, Context context) {
		try {
		logger = context.getLogger();
		AppService service = new AppService();
		Map<String, String> resultCodes = loadResultCodeProperties();
		logger.log("[POINTEL] - handleRequest() - Download Outbound CSV file  Process Started");

		String campaignName = System.getenv("campaignName");

		logger.log("[POINTEL] - handleRequest() - campaignName : " + campaignName);
		String contactListName = service.getContactListName(campaignName);
		return service.getLatestBackupData(s3Client, contactListName, resultCodes);
		
		} catch (Exception e) {
			e.printStackTrace();
			AppController.logger.log("[POINTEL] - exportContactList() - ()" + Sticky.printStackTrace(e));
		}
		return "Failed to send email!";
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

