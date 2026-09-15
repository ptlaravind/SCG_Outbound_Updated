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

public class AppController implements RequestHandler<S3Event, String> {

	private AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

	public static LambdaLogger logger;

	@Override
	public String handleRequest(S3Event s3Event, Context context) {

		logger = context.getLogger();
		AppService service = new AppService();
		logger.log("[POINTEL] - handleRequest() - Upload Outbound call details into Genesys Process Started");
		String bucketName = s3Event.getRecords().get(0).getS3().getBucket().getName();
		String objectKey = s3Event.getRecords().get(0).getS3().getObject().getKey();
		logger.log("[POINTEL] - handleRequest() - S3 bucket name -" + bucketName + " and Object key-" + objectKey);

		try {
			// Extract the folder name (prefix) to get exact folder name
			String decodedKey = URLDecoder.decode(objectKey, "UTF-8");
			logger.log("[POINTEL] - handleRequest() - S3 bucket name -" + bucketName + " and decoded Object key-"
					+ decodedKey);
			//String campaignName = extractFolderName(decodedKey);
			String campaignName = System.getenv("campaignName");
			logger.log("[POINTEL] - handleRequest() - Campaign Name: " + campaignName);
			S3Object s3Object = s3Client.getObject(bucketName, decodedKey);
			S3ObjectInputStream inputStream = s3Object.getObjectContent();
			String content = new BufferedReader(new InputStreamReader(inputStream)).lines()
					.collect(Collectors.joining("\n"));

			List<Map<String, String>> outboundDetails = new ArrayList<>();
			outboundDetails = service.getOutboundDetails(content);
			logger.log("[POINTEL] - handleRequest() - Campaign name -" + campaignName);
			logger.log("[POINTEL] - handleRequest() - outbound Details Request List- " + outboundDetails);

			String contactListId = service.getContactListId(campaignName);
			if (contactListId != null && !contactListId.isEmpty()) {
				List<String> columnNames = service.getColumnNames(contactListId);
				service.uploadCampaignContactList(columnNames, contactListId, outboundDetails);
				inputStream.close();
				logger.log("[POINTEL] - handleRequest() - S3 Object Content:-" + content);
				logger.log("[POINTEL] - handleRequest() - Upload Outbound call details into Genesys Process Ended");
			} else {
				logger.log("[POINTEL] - handleRequest() - Invalid Campaign or ContactList not found ");
			}
			return content;
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

//	private String extractFolderName(String objectKey) {
//		try {
//			String folderName = "";
//			int lastSlashIndex = objectKey.lastIndexOf('/');
//			if (lastSlashIndex > 0) {
//				// The folder is everything before the last slash
//				folderName = objectKey.substring(0, lastSlashIndex);
//				if (folderName.contains("/")) {
//					String[] tempFolderName = folderName.split("/");
//					if (tempFolderName.length > 1) {
//						folderName = tempFolderName[tempFolderName.length - 1];
//					}
//				}
//				return folderName;
//			} else {
//				// If no folder, return the root
//				return "Root Folder";
//			}
//		} catch (Exception exception) {
//			logger.log("[POINTEL] - handleRequest() - ()" + Sticky.printStackTrace(exception));
//			exception.printStackTrace();
//			return "Error reading S3 object";
//		}
//
//	}
}