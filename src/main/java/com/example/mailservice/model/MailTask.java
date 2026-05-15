package com.example.mailservice.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class MailTask {
    private final String id;
    private final boolean blast;
    private final Integer userId;
    private final List<String> recipients;
    private final String subject;
    private final String body;
    private final OffsetDateTime createdAt;

    private MailTask(boolean blast, Integer userId, List<String> recipients, String subject, String body) {
        this.id = UUID.randomUUID().toString();
        this.blast = blast;
        this.userId = userId;
        this.recipients = recipients;
        this.subject = subject;
        this.body = body;
        this.createdAt = OffsetDateTime.now();
    }

    public static MailTask uploadNotification(int userId) {
        return new MailTask(false, userId, null,
                "Таны profile зураг амжилттай upload хийгдлээ!",
                "Сайн байна уу!\nТаны профайл зураг амжилттай upload хийгдлээ.\nБаярлалаа! Өдрийг сайхан өнгөрүүлээрэй.");
    }

    public static MailTask blast(List<String> recipients, String subject, String body) {
        return new MailTask(true, null, recipients, subject, body);
    }

    public String getId() {
        return id;
    }

    public boolean isBlast() {
        return blast;
    }

    public Integer getUserId() {
        return userId;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
