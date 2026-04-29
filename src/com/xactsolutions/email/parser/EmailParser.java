package com.xactsolutions.email.parser;

import com.xactsolutions.email.exception.HtmlContentNotFoundException;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author basharkhan6
 * @apiNote This class requires latest version of following package to work properly
 * <li>Angus Jakarta Mail</li>
 * <li>Angus Activation API (Runtime only)</li>
 * <li>Jakarta Activation API (Runtime only)</li>
 * */
public class EmailParser {

    private static final String MIME_TYPE_TEXT = "text/plain";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_MIXED = "multipart/mixed";
    private static final String MIME_TYPE_REPORT = "multipart/report";
    private static final String MIME_TYPE_RELATED = "multipart/related";
    private static final String MIME_TYPE_ALTERNATIVE = "multipart/alternative";
    private static final String MIME_RFC_822_HEADER = "message/rfc822-headers";
    private static final String REPORT_SUB_TYPE = "report-type=";

    private final MimeMessage message;

    public EmailParser(String rawFilename) {
        try {
            FileInputStream fileInputStream = new FileInputStream(rawFilename);
            this.message = new MimeMessage(Session.getInstance(new Properties()), fileInputStream);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Raw file not found. " + rawFilename, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse", e);
        }
    }

    public EmailParser(ByteArrayInputStream is) {
        try {
            this.message = new MimeMessage(Session.getDefaultInstance(new Properties()), is);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to parse", e);
        }
    }

    public Address getFrom() {
        try {
            return message.getFrom()[0];
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Address> getRecipients() {
        try {
            return new ArrayList<>(Arrays.asList(message.getAllRecipients()));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getSubject() {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public Date getSentDate() {
        try {
            return message.getSentDate();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isReport() {
        try {
            return getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_REPORT);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getReportSubType() {
        try {
            String contentType = message.getContentType().toLowerCase();
            int startIndex = contentType.indexOf(REPORT_SUB_TYPE);
            if (startIndex != -1) {
                int endIndex = contentType.indexOf(";", startIndex);
                if (endIndex != -1)
                    return contentType.substring(startIndex+REPORT_SUB_TYPE.length(), endIndex);
                return contentType.substring(startIndex+REPORT_SUB_TYPE.length());
            }
            return null;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTextContent() {
        try {
            if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_TEXT)
                && message.getContent() instanceof String textContent) return textContent;
            else if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_ALTERNATIVE)
                && message.getContent() instanceof MimeMultipart alternativeContent) {
                Object bodyPart = getBodyPart(alternativeContent, MIME_TYPE_TEXT);
                if (bodyPart instanceof String textContent) return textContent;
            } else if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_MIXED)
                && message.getContent() instanceof MimeMultipart mixedContent) {
                Object bodyPart = getBodyPart(mixedContent, MIME_TYPE_TEXT);
                if (bodyPart instanceof String textContent) return textContent;
                bodyPart = getBodyPart(mixedContent, MIME_TYPE_ALTERNATIVE);
                if (bodyPart instanceof MimeMultipart alternativeContent) {
                    bodyPart = getBodyPart(alternativeContent, MIME_TYPE_TEXT);
                    if (bodyPart instanceof String textContent) return textContent;
                }
            }
            throw new RuntimeException("There is no text content found in this email!!");
        } catch (MessagingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getHtmlContent() {
        try {
            if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_HTML)
                && message.getContent() instanceof String htmlContent) return htmlContent;
            else if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_ALTERNATIVE)
                && message.getContent() instanceof MimeMultipart alternativeContent) {
                Object bodyPart = getBodyPart(alternativeContent, MIME_TYPE_HTML);
                if (bodyPart instanceof String htmlContent) return htmlContent;
            } else if (getBaseType(message.getContentType()).equalsIgnoreCase(MIME_TYPE_MIXED)
                && message.getContent() instanceof MimeMultipart mixedContent) {
                Object bodyPart = getBodyPart(mixedContent, MIME_TYPE_HTML);
                if (bodyPart instanceof String htmlContent) return htmlContent;
                bodyPart = getBodyPart(mixedContent, MIME_TYPE_ALTERNATIVE);
                if (bodyPart instanceof MimeMultipart alternativeContent) {
                    bodyPart = getBodyPart(alternativeContent, MIME_TYPE_HTML);
                    if (bodyPart instanceof String htmlContent) return htmlContent;
                    bodyPart = getBodyPart(alternativeContent, MIME_TYPE_RELATED);
                    if (bodyPart instanceof MimeMultipart relatedContent) {
                        bodyPart = getBodyPart(relatedContent, MIME_TYPE_HTML);
                        if (bodyPart instanceof String htmlContent) return htmlContent;
                    }
                }
            }
            throw new HtmlContentNotFoundException();
        } catch (MessagingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EmailParser extractOriginalReportedEmailHeader() {
        try {
            if (message.getContent() instanceof MimeMultipart content) {
                Object bodyPart = getBodyPart(content, MIME_RFC_822_HEADER);
                if (bodyPart instanceof ByteArrayInputStream originalHeader) {
                    return new EmailParser(originalHeader);
                }
            }
            throw new RuntimeException("Invalid email content to extract original email header from body");
        } catch (MessagingException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getBaseType(String contentType) throws MessagingException {
        if (contentType == null || contentType.isBlank()) return contentType;

        int baseTypeIndex = contentType.indexOf(";");
        if (baseTypeIndex == -1) return contentType;

        return contentType.substring(0, baseTypeIndex);
    }

    private Object getBodyPart(MimeMultipart multipart, String type) throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);
            if (getBaseType(bodyPart.getContentType()).equalsIgnoreCase(type)) {
                return bodyPart.getContent();
            }
        }
        return null;
    }

}
