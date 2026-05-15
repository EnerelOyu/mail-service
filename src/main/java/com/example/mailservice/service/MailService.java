package com.example.mailservice.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.mailservice.model.EmailRequest;
import com.example.mailservice.model.MailTask;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    @Value("${sendgrid.api.key}")
    private String sendgridApiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${mail.user-service.timeout-ms:3000}")
    private int userServiceTimeoutMs;

    @Value("${mail.sendgrid.timeout-ms:5000}")
    private int sendGridTimeoutMs;

    private final RestTemplate restTemplate;
    private final Executor mailExecutor;
    private final BlockingQueue<MailTask> queue;


    public MailService(RestTemplate restTemplate,
                       @Qualifier("mailTaskExecutor") Executor mailExecutor,
                       @Value("${mail.queue.capacity:500}") int queueCapacity) {
        this.restTemplate = restTemplate;
        this.mailExecutor = mailExecutor;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
    }

    public Map<String, Object> enqueueUploadNotification(EmailRequest request) {
        try {
            int userId = Integer.parseInt(request.getTo());
            MailTask task = MailTask.uploadNotification(userId);
            boolean accepted = queue.offer(task, 500, TimeUnit.MILLISECONDS);
            if (accepted) {
                logger.info("timestamp={} service=mail-service operation=queueUploadNotification userId={} status=QUEUED",
                        OffsetDateTime.now(), userId);
                return Map.of(
                        "success", true,
                        "accepted", true,
                        "status", "QUEUED",
                        "message", "Mail хүсэлт амжилттай бүртгэгдлээ. Имэйл дараа нь илгээгдэнэ.");
            }
            return fallbackResponse("queue full", userId, null);
        } catch (Exception ex) {
            logger.error("timestamp={} service=mail-service operation=queueUploadNotification status=FAILED error=\"{}\"",
                    OffsetDateTime.now(), ex.getMessage(), ex);
            return fallbackResponse("invalid request", null, ex.getMessage());
        }
    }

    public Map<String, Object> enqueueBlast(EmailRequest request) {
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            return Map.of(
                    "success", false,
                    "accepted", false,
                    "status", "INVALID_REQUEST",
                    "message", "Recipients жагсаалт хоосон байна.");
        }
        MailTask task = MailTask.blast(request.getRecipients(), request.getSubject(), request.getBody());
        try {
            boolean accepted = queue.offer(task, 500, TimeUnit.MILLISECONDS);
            if (accepted) {
                logger.info("timestamp={} service=mail-service operation=queueBlast status=QUEUED recipients={} count={}",
                        OffsetDateTime.now(), request.getRecipients().size(), request.getRecipients().size());
                return Map.of(
                        "success", true,
                        "accepted", true,
                        "status", "QUEUED",
                        "message", "Бласт имэйл хүсэлт амжилттай бүртгэгдлээ.");
            }
            return fallbackResponse("queue full", null, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallbackResponse("interrupted", null, ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${mail.queue.poll.delay-ms:2000}")
    public void processQueue() {
        while (!queue.isEmpty()) {
            MailTask task = queue.poll();
            if (task == null) {
                return;
            }
            try {
                sendTask(task);
            } catch (Exception ex) {
                logger.error("timestamp={} service=mail-service operation=processQueue taskId={} status=FAILED error=\"{}\"",
                        OffsetDateTime.now(), task.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void sendTask(MailTask task) {
        if (task.isBlast()) {
            for (String email : task.getRecipients()) {
                sendEmailWithRetry(email, task.getSubject(), task.getBody(), task);
            }
            return;
        }
        String email = resolveEmail(task.getUserId());
        if (email == null) {
            logger.warn("timestamp={} service=mail-service operation=resolveEmail userId={} status=NO_EMAIL",
                    OffsetDateTime.now(), task.getUserId());
            return;
        }
        sendEmailWithRetry(email, task.getSubject(), task.getBody(), task);
    }

    private String resolveEmail(Integer userId) {
        if (userId == null) {
            return null;
        }
        try {
            String url = userServiceUrl + "/users/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service", "mail-service");
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("email");
            }
            logger.error("timestamp={} service=mail-service operation=resolveEmail userId={} status=HTTP_{}",
                    OffsetDateTime.now(), userId, response.getStatusCodeValue());
        } catch (RestClientException ex) {
            logger.error("timestamp={} service=mail-service operation=resolveEmail userId={} status=FAILED error=\"{}\"",
                    OffsetDateTime.now(), userId, ex.getMessage(), ex);
        }
        return null;
    }

    @Retryable(value = {IOException.class, TimeoutException.class}, maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendEmailWithRetry(String to, String subject, String body, MailTask task) {
        try {
            logger.info("timestamp={} service=mail-service operation=sendEmailWithRetry taskId={} to={} attempt=START",
                    OffsetDateTime.now(), task.getId(), to);
            sendEmailWithTimeout(to, subject, body);
            logger.info("timestamp={} service=mail-service operation=sendEmailWithRetry taskId={} to={} status=SENT",
                    OffsetDateTime.now(), task.getId(), to);
        } catch (IOException | TimeoutException ex) {
            logger.warn("timestamp={} service=mail-service operation=sendEmailWithRetry taskId={} to={} status=RETRY error=\"{}\"",
                    OffsetDateTime.now(), task.getId(), to, ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Recover
    private void recoverSendEmail(RuntimeException ex, String to, String subject, String body, MailTask task) {
        logger.error("timestamp={} service=mail-service operation=recoverSendEmail taskId={} to={} status=FAILED retriesExceeded=3 error=\"{}\"",
                OffsetDateTime.now(), task.getId(), to, ex.getMessage(), ex);
    }

    private void sendEmailWithTimeout(String to, String subject, String body) throws IOException, TimeoutException {
        Email from = new Email(fromEmail);
        Email toEmail = new Email(to);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, toEmail, content);

        SendGrid sg = new SendGrid(sendgridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        try {
            CompletableFuture<Response> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return sg.api(request);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, mailExecutor);
            Response response = future.get(sendGridTimeoutMs, TimeUnit.MILLISECONDS);
            if (response.getStatusCode() >= 400) {
                throw new IOException("SendGrid returned status " + response.getStatusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("SendGrid interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("SendGrid execution failed", ex);
        }
    }

    private Map<String, Object> fallbackResponse(String reason, Integer userId, String details) {
        logger.error("timestamp={} service=mail-service operation=fallback status=FALLBACK reason={} userId={} details=\"{}\"",
                OffsetDateTime.now(), reason, userId, details);
        return Map.of(
                "success", true,
                "accepted", false,
                "status", "FALLBACK",
                "message", "Mail сервис түр зуур асуудалтай байна. Үйл ажиллагаа амжилттай, мэдэгдэл илгээхгүй.",
                "reason", reason);
    }
}

