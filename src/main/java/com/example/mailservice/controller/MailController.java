package com.example.mailservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.mailservice.model.EmailRequest;
import com.example.mailservice.service.MailService;

@RestController
@RequestMapping("/mail")
@CrossOrigin
public class MailController {
	private final MailService mailService;
	
	public MailController(MailService mailService) {
		this.mailService = mailService;
	}
	
	//profile image upload хийхэд дуудагдах endpoint
	@PostMapping("/uploaded-notif")
	public ResponseEntity<?> sendUploadedNotifcation(@RequestBody EmailRequest request){
		try {
			mailService.sendImageUploadedNotif(Integer.parseInt(request.getTo()));
			return ResponseEntity.ok(Map.of("success", true, "message", "Mail илгээгдлэ."));
		}catch(Exception e){
			return ResponseEntity.internalServerError().body(Map.of("success", false,"message", e.getMessage()));
		}
	}
	
	//weekly blast
	@PostMapping("/weekly-blast")
	public ResponseEntity<?> sendBlastToList(@RequestBody EmailRequest request){
		try {
			mailService.sendBlastToList(request.getRecipients(), request.getSubject(), request.getBody());
			return ResponseEntity.ok(Map.of("success", true, "message", "blast илгээгдлэ."));
		}catch(Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("success", false,"message", e.getMessage()));
		}
	}

}
