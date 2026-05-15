package com.example.mailservice.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
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

    @PostMapping("/uploaded-notif")
    public ResponseEntity<Map<String, Object>> sendUploadedNotification(@RequestBody EmailRequest request) {
        Map<String, Object> result = mailService.enqueueUploadNotification(request);
        HttpStatus status = (Boolean.TRUE.equals(result.get("accepted"))) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return new ResponseEntity<>(result, status);
    }

    @PostMapping("/weekly-blast")
    public ResponseEntity<Map<String, Object>> sendBlastToList(@RequestBody EmailRequest request) {
        Map<String, Object> result = mailService.enqueueBlast(request);
        HttpStatus status = (Boolean.TRUE.equals(result.get("accepted"))) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return new ResponseEntity<>(result, status);
    }
}

