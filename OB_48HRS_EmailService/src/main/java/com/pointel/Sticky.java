package com.pointel;

import java.io.PrintWriter;
import java.io.StringWriter;

/*************************************************************************
 *
 * POINTEL INC CONFIDENTIAL
 *
 * __________________
 *
 *
 * All Rights Reserved.
 *
 *
 * NOTICE: The intellectual and technical concepts contained
 *
 * herein are proprietary to Pointel Inc Incorporated
 *
 * Dissemination of this information or reproduction of this material
 *
 * is strictly forbidden unless prior written permission is obtained
 *
 * from Pointel Inc Incorporated.
 *
 */

public class Sticky {

	private Sticky() {
		throw new IllegalStateException("Sticky class");
	}

	public static String printStackTrace(Exception paramException) {
		StringWriter localStringWriter = new StringWriter();
		paramException.printStackTrace(new PrintWriter(localStringWriter));
		return localStringWriter.toString();
	}

}
