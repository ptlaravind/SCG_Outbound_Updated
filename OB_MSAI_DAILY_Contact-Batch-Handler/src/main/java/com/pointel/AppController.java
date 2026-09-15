package com.pointel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

public class AppController implements RequestHandler<String, String> {

	private AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

	public static LambdaLogger logger;

	@Override
	public String handleRequest(String input, Context context) {

		logger = context.getLogger();
		AppService service = new AppService();
		logger.log("[POINTEL] - handleRequest() - Upload Outbound call details into Genesys Process Started");
		logger.log("[POINTEL] - handleRequest() - input "+input);
		
		
		
		//String bucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
		//String objectKey = s3Event.getRecords().get(0).getS3().getObject().getKey();
		//logger.log("[POINTEL] - handleRequest() - S3 bucket name -" + bucketName + " and Object key-" + objectKey);

		try {
			// Extract the folder name (prefix) to get exact folder name
			//String decodedKey = URLDecoder.decode(objectKey, "UTF-8");
			//logger.log("[POINTEL] - handleRequest() - S3 bucket name -" + bucketName + " and decoded Object key-"
			//		+ decodedKey);
			//String campaignName = extractFolderName(decodedKey);
			String campaignName = System.getenv("campaignName");
			String bucketName = System.getenv("backupbucket");
			
			String fileName = input.trim().split(":")[1];//Coming from scheduler input
			String filePath = System.getenv("filePath")+fileName;
			logger.log("[POINTEL] - handleRequest() - Campaign Name: " + campaignName);
			
			S3Object s3Object = s3Client.getObject(bucketName, filePath);
			if(s3Client.doesObjectExist(bucketName, filePath)) {
				
			
			S3ObjectInputStream inputStream = s3Object.getObjectContent();
			String content = new BufferedReader(new InputStreamReader(inputStream)).lines()
					.collect(Collectors.joining("\n"));

			List<Map<String, String>> outboundDetails = new ArrayList<>();
			outboundDetails = service.getMSAIDailyOutboundDetails(content);
			logger.log("[POINTEL] - handleRequest() - Campaign name -" + campaignName);
			logger.log("[POINTEL] - handleRequest() - outbound Details Request List- " + outboundDetails);

			String contactListId = service.getContactListId(campaignName);
			if (contactListId != null && !contactListId.isEmpty()) {
				List<String> columnNames = service.getColumnNames(contactListId);
				service.uploadCampaignContactList(columnNames, contactListId, outboundDetails, bucketName, filePath);
				inputStream.close();
				logger.log("[POINTEL] - handleRequest() - S3 Object Content:-" + content);
				logger.log("[POINTEL] - handleRequest() - Upload Outbound call details into Genesys Process Ended");
				
			} else {
				logger.log("[POINTEL] - handleRequest() - Invalid Campaign or ContactList not found ");
			}
			return content;
			}else {
				logger.log("[POINTEL] - handleRequest() - File not available from S3 bucket "+filePath);
				return "File not available from S3 bucket "+filePath;
			}
		} catch (IOException exception) {
			logger.log("[POINTEL] - handleRequest() - ()" + exception.getMessage());
			logger.log("[POINTEL] - handleRequest() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
			return "Error reading S3 object";
		} catch (Exception exception) {
			logger.log("[POINTEL] - handleRequest() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
			return "Error reading S3 object";
		}
	}
}