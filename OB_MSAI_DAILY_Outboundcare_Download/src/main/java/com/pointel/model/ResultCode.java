package com.pointel.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "OutboundDispositionCode")
public class ResultCode {
	
	@DynamoDBHashKey(attributeName = "Disposition_Code")
	private String resultCode;
	@DynamoDBAttribute(attributeName = "Code_Description")
	private String codeDescription;
	public String getResultCode() {
		return resultCode;
	}
	public void setResultCode(String resultCode) {
		this.resultCode = resultCode;
	}
	public String getCodeDescription() {
		return codeDescription;
	}
	public void setCodeDescription(String codeDescription) {
		this.codeDescription = codeDescription;
	}
	@Override
	public String toString() {
		return "ResultCode [resultCode=" + resultCode + ", codeDescription=" + codeDescription + "]";
	}
	
}
