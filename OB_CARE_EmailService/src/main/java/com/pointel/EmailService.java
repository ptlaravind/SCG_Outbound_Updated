package com.pointel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class EmailService {

	private static final String SMTP_HOST = System.getenv("smtpHost");
	private static final int SMTP_PORT = Integer.parseInt(System.getenv("smtpPort"));

	public String sendEmail(String subject, Multipart multipartFile) {
		CryptoService cryptoService = new CryptoService();
		Properties properties = new Properties();
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.host", SMTP_HOST);
		properties.put("mail.smtp.port", SMTP_PORT);
		properties.put("mail.smtp.ssl.trust", SMTP_HOST);
		String response = "";
		try {
			//AppService service = new AppService();
			
			
			//ip
			 	URL whatIsMyIp = new URL("https://api.ipify.org");

	            BufferedReader in = new BufferedReader(new InputStreamReader(
	                    whatIsMyIp.openStream()));
	            String ip = in.readLine();
	            in.close();

	            AppController.logger.log(" sendEmail() Public IP Address is: " + ip);
			//
		//String decryptedPassword = cryptoService.decrypt(AppService.password);
		Session session = Session.getInstance(properties, new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(AppService.email, AppService.password);
			}
		});
		
		
			String recipient = System.getenv("send_Recipients");
			String ccRecipients = System.getenv("send_CC_Recipients");
			Message message = new MimeMessage(session);
			//message.setFrom(new InternetAddress(USERNAME));
			message.setFrom(new InternetAddress(System.getenv("fromEmail"),System.getenv("headerFromEmail")));
			
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
			if (ccRecipients != null && !ccRecipients.isEmpty()) {
				message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccRecipients.trim()));
			}
			message.setSubject(subject);
			message.setContent(multipartFile);

			Transport.send(message);
			AppController.logger.log("[POINTEL] - sendEmail() Email sent successfully to " + recipient);
			response = "Email sent successfully!";
			

			return response;
		} catch (Exception e) {
			e.printStackTrace();
			AppController.logger.log("[POINTEL] - sendEmail() Error sending email: " + e.getMessage());
		}
		response = "Failed to send mail!";
		return response;
	}
	
}
