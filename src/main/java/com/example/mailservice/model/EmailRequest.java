package com.example.mailservice.model;

import java.time.LocalDateTime;
import java.util.List;

public class EmailRequest {
	private String to;
	private String subject;
	private String body;
	//blast email huleej awaah useruud
	private List<String> recipients;
	private LocalDateTime date;
	
	//getter && setter functions
	public String getTo() {
		return to;
	}
	
	public void setTo(String to) {
		this.to = to;
	}
	
	public String getSubject() {
		return subject;
	}
	
	public void setSubject(String subject) {
		this.subject = subject;
	}
	
	public String getBody() {
		return body;
	}
	
	public void setBody(String body) {
		this.body = body;
	}
	
	public List<String>getRecipients(){
		return recipients;
	}
	
	public void setRecipients(List<String>recipients) {
		this.recipients = recipients;
	}
	
	public LocalDateTime getDate() {
		return date;
	}
	
	public void setDate(LocalDateTime date) {
		this.date = date;
	}
	
}
