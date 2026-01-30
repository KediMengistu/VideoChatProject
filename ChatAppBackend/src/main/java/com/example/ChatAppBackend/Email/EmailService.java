package com.example.ChatAppBackend.Email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from:}")
    private String resendFrom;

    public void sendEmail(String rawRoomKeyCode, String inviteeEmail) {
        if (inviteeEmail == null || inviteeEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("inviteeEmail cannot be null/blank");
        }
        if (rawRoomKeyCode == null || rawRoomKeyCode.trim().isEmpty()) {
            throw new IllegalArgumentException("rawRoomKeyCode cannot be null/blank");
        }
        if (resendApiKey == null || resendApiKey.trim().isEmpty()) {
            logger.error("Resend API key is missing. Ensure resend.api-key is set in application.properties.");
            throw new IllegalStateException("Email service is not configured (missing Resend API key).");
        }

        String from = (resendFrom != null && !resendFrom.trim().isEmpty())
                ? resendFrom.trim()
                : "ChatApp <onboarding@resend.dev>";

        String subject = "Your ChatApp room key code (expires in 15 minutes)";
        String textBody =
                "You have been invited to a ChatApp room.\n\n" +
                        "Room Key Code: " + rawRoomKeyCode + "\n" +
                        "This code expires in 15 minutes.\n";

        String htmlBody =
                "<div style=\"font-family: Arial, sans-serif; line-height: 1.5;\">" +
                        "<h2>ChatApp Room Invitation</h2>" +
                        "<p>You have been invited to a ChatApp room.</p>" +
                        "<p><b>Room Key Code:</b> <code style=\"font-size: 1.1em;\">" + rawRoomKeyCode + "</code></p>" +
                        "<p>This code expires in <b>15 minutes</b>.</p>" +
                        "</div>";

        try {
            Resend resend = new Resend(resendApiKey);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(from)
                    .to(inviteeEmail.trim().toLowerCase())
                    .subject(subject)
                    .text(textBody)
                    .html(htmlBody)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);

            logger.info("Invitation email sent via Resend to {}. Resend id={}",
                    inviteeEmail, response.getId());

        } catch (ResendException re) {
            logger.error("ResendException while sending invite email to {}: {}",
                    inviteeEmail, re.getMessage(), re);
            throw new RuntimeException("Failed to send invitation email (Resend).", re);

        } catch (Exception e) {
            logger.error("Unexpected error while sending invite email to {}: {}",
                    inviteeEmail, e.getMessage(), e);
            throw new RuntimeException("Failed to send invitation email.", e);
        }
    }
}
