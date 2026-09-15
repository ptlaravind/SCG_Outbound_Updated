package com.pointel;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class AppController implements RequestHandler<String, String> {

	public static LambdaLogger logger;

	@Override
	public String handleRequest(String input, Context context) {

		logger = context.getLogger();
		AppService service = new AppService();
		logger.log("[POINTEL] - handleRequest() -Clearing contact list Process Started");
		logger.log("[POINTEL] - handleRequest() - input "+input);

		try {

			String campaignName = input.trim().split(":")[1];//Coming from scheduler input
			logger.log("[POINTEL] - handleRequest() - Campaign Name: " + campaignName);
			String contactListInfo = service.getContactListInfo(campaignName);
			String[] contactListArray = contactListInfo.split("\\|");
			String contactListId = contactListArray[0];
			
			if(campaignName != null && !campaignName.isEmpty()) {
				String campaignId = service.getCampaignIdByName(campaignName);
				if(campaignId != null && !campaignId.isEmpty()) {
					service.forceStopSafely(campaignId);
					service.clearConactList(contactListId);
					return "ContactList clered successfully";
				}
			}else {
				logger.log("[POINTEL] - handleRequest() - campaignName not found ");
				return "campaignName not found...";
			}
		} catch (Exception exception) {
			logger.log("[POINTEL] - handleRequest() - ()" + Sticky.printStackTrace(exception));
			exception.printStackTrace();
			return "Error clearing contactList";
		}
		return "Error clearing contactList";
	}
}