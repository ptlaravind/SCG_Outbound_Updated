//package com.pointel.test;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.net.URL;
//import java.util.Properties;
//
//import com.pointel.CryptoService;
//
//import jakarta.mail.Authenticator;
//import jakarta.mail.Message;
//import jakarta.mail.PasswordAuthentication;
//import jakarta.mail.Session;
//import jakarta.mail.Transport;
//import jakarta.mail.internet.InternetAddress;
//import jakarta.mail.internet.MimeMessage;
//
//public class Test {
//	
////	public static void main(String[] args) {
////        try {
////            URL whatIsMyIp = new URL("https://api.ipify.org");
////
////            BufferedReader in = new BufferedReader(new InputStreamReader(
////                    whatIsMyIp.openStream()));
////            String ip = in.readLine();
////            in.close();
////
////            System.out.println(" Public IP Address is: " + ip);
////        } catch (Exception e) {
////            System.err.println("Error fetching public IP: " + e.getMessage());
////            e.printStackTrace();
////        }
////    }
//	
//	public static void main(String[] args) {
//
//
//
//		String SMTP_HOST = "smtp-us.ser.proofpoint.com";
//		String USERNAME = "2dabbe04-f0b5-4bea-8347-7ca16a2ee9a9";
////		String PASSWORD = "xHesZjwuCajfvc1";
//		String PASSWORD = "UOEPcPKZMNjpn60vfMXl9w==";
//		
//		int SMTP_PORT = 587;
//			Properties properties = new Properties();
//			properties.put("mail.smtp.auth", "true");
//			properties.put("mail.smtp.starttls.enable", "true");
//			properties.put("mail.smtp.host", SMTP_HOST);
//			properties.put("mail.smtp.port", SMTP_PORT);
//			properties.put("mail.smtp.ssl.trust", SMTP_HOST);
//			properties.put("mail.smtp.port", "587");
//			//properties.put("mail.smtp.auth.mechanisms", "LOGIN PLAIN");
//			String response = "";
//			try {
//			CryptoService cservice = new CryptoService();
//			Session session = Session.getInstance(properties, new Authenticator() {
//				protected PasswordAuthentication getPasswordAuthentication() {
//					try {
//						return new PasswordAuthentication(USERNAME, cservice.decrypt(PASSWORD));
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					return null;
//				}
//			});
//			
//			
//				String recipient = "aravind.s@pointelsolutions.com";
//				Message message = new MimeMessage(session);
//				message.setFrom(new InternetAddress("SCGCCCNoReply@socalgas.com","SCGCCCNoReply@socalgas.com"));
//				message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
//				
//				message.setSubject("Test");
//				message.setText("Testing....");
//
//				Transport.send(message);
//				System.out.println("[POINTEL] - sendEmail() Email sent successfully to " + recipient);
//				response = "Email sent successfully!";
//			} catch (Exception e) {
//				e.printStackTrace();
//				System.out.println("[POINTEL] - sendEmail() Error sending email: " + e.getMessage());
//			}
//			response = "Failed to send mail!";
//
//	}
//
//}
