package com.example.mailservice.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MailService {
	
	private final JavaMailSender mailSender;
	private final RestClient restClient;
	
	@Value("${user.service.url}")
	private String userServiceUrl;
	
	public MailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
		this.restClient = RestClient.create();
	}
	
	private Map<String, Object> fetchUser(int userId){
		return restClient.get()
				.uri(userServiceUrl + "/users/" + userId)
				.header("X-Internal-Service", "mail-service")
				.retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>(){});
	}
	
	//зураг амжилттай upload хийгдсэн үед тухайн хэрэглэгч руу амжилтийн мэдэгдэл илгээх.
	public void sendImageUploadedNotif(int userId) {
		Map<String, Object> user = fetchUser(userId);
		String email = (String)user.get("email");
		String username = (String)user.get("username");
		
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);
		message.setSubject("Таны profile зураг амжилттай upload хийгдлээ!");
		message.setText(
				"Сайн байна уу," + username + "!\n" +
		        "Таны профайл зураг амжилттай upload хийгдлээ.\n" +
				"Баярлалаа! Өдрийг сайхан өнгөрүүлээрэй."
		);
		mailSender.send(message);
	}
	
	//7 хоног бүрийн mail blast
	@Scheduled(cron = "0 0 11 * * MON")
	public void sendWeeklyBlast() {
		 List<Map<String, Object>> users = restClient.get()
	                .uri(userServiceUrl + "/users")
	                .header("X-Internal-Service", "mail-service")
	                .retrieve()
	                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
		if(users == null || users.isEmpty()) {
			return;
		}
		
		for (Map<String, Object> user : users) {
			String email = (String) user.get("email");
			String username = (String) user.get("username");
			if(email == null) {
				continue;
			}
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(email);
			message.setSubject("Долоо хоногийн мэдээ.");
			message.setText(
					"Сайн байна уу,"+ username + "!\n"+
					"Энэ долоо хоногт манай системд тавтай морилно уу.\n"+
					"Баярлалаа!"
			);
			mailSender.send(message);
			
		}
	}
	
	public void sendBlastToList(List<String>emails, String subject, String body) {
		for(String email : emails) {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(email);
			message.setSubject(subject);
			message.setText(body);
			mailSender.send(message);
		}
		
	}
	
}
