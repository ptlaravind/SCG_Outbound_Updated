package com.pointel.model;

import java.util.List;
import java.util.Map;

public class OutboundResult {
	private boolean processCsvData;
	private List<Map<String, String>> dataList;
	public boolean isProcessCsvData() {
		return processCsvData;
	}
	public void setProcessCsvData(boolean processCsvData) {
		this.processCsvData = processCsvData;
	}
	public List<Map<String, String>> getDataList() {
		return dataList;
	}
	public void setDataList(List<Map<String, String>> dataList) {
		this.dataList = dataList;
	}
	@Override
	public String toString() {
		return "OutboundResult [processCsvData=" + processCsvData + ", dataList=" + dataList + "]";
	}
	
	

	
	

}
