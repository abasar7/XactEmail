package com.xactsolutions.email.handler;

import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.model.MessageMeta;
import com.xactsolutions.email.parser.EmailParser;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WebhookProcessEmailsHandler extends BaseRequestHandler {

    private final Maddy maddy;
    private final String messageDir;
    private final URI crmInboundUri;
    private final URI crmReportUri;
    private final HttpClient httpClient;

    private static LocalDateTime lastScannedTime = LocalDateTime.now();
    private static final AtomicBoolean scanning = new AtomicBoolean(false);

    public WebhookProcessEmailsHandler(String endpoint, Maddy maddy, String messageDir, String crmInboundUrl, String crmReportUrl) {
        super(endpoint);
        this.maddy = maddy;
        this.messageDir = messageDir;
        this.crmInboundUri = URI.create(crmInboundUrl);
        this.crmReportUri = URI.create(crmReportUrl);
        this.httpClient = HttpClient.newHttpClient();
    }

    public void triggerProcessing() {
        this.post();
    }

    @Override
    protected @NonNull Response post() {
        if (lastScannedTime.plusSeconds(10).isAfter(LocalDateTime.now())) {
            log.info("Skip the incoming message scanning to ensure min delay (10s)");
            return new JsonResponse(200, null);
        }
        if (scanning.get()) {
            log.info("Skipping... Another thread is scanning incoming message.");
            return new JsonResponse(200, null);
        }

        Thread.ofVirtual()
            .name("imap-message-scanner", 1)
            .start(() -> {
                scanning.set(true);
                try {
                    List<MessageMeta> messages = maddy.listUnseenMessages();
                    log.debug("Total {} messages has been fetched as unseen", messages.size());

                    List<Long> regularIds = new ArrayList<>();
                    List<Long> reportIds = new ArrayList<>();
                    for (var meta : messages) {
                        long id = meta.getId();
                        String bodyKey = meta.getBodyKey();
                        try {
                            EmailParser parser = new EmailParser(messageDir + bodyKey);
                            boolean report = parser.isReport();
                            if (report && parser.getReportSubType().equals("delivery-status")) {
                                EmailParser newParser = parser.extractOriginalReportedEmailHeader();
                                String crmPayload = buildCRMPayload(newParser, true);
                                sendAsyncRequest(crmReportUri, crmPayload);
                                reportIds.add(id);
                            } else if (!report) {
                                String crmPayload = buildCRMPayload(parser, false);
                                sendAsyncRequest(crmInboundUri, crmPayload);
                                regularIds.add(id);
                            }
                        } catch (Exception e) {
                            log.error("Failed to forward message {}-{} to {}", id, bodyKey, crmInboundUri, e);
                        }
                    }

                    List<Long> allForwardedIds = new ArrayList<>(regularIds);
                    allForwardedIds.addAll(reportIds);

                    log.debug("Total {}/{} messages has been forwarded (will be mark as soon). " +
                            "Where {} was regular and {} was report.",
                        allForwardedIds.size(), messages.size(), reportIds.size(), reportIds.size());
                    maddy.markMessagesAsSeen(allForwardedIds);
                } catch (Exception e) {
                    log.error("Error in fetching/updating IMAP messages from DB", e);
                } finally {
                    lastScannedTime = LocalDateTime.now();
                    scanning.set(false);
                }
            });

        return new JsonResponse(200, null);
    }

    private String buildCRMPayload(EmailParser parser, boolean report) {
        String fromEmail = parser.getFrom().toString();
        String fromName = parser.getFrom().toString();
        if (parser.getFrom() instanceof InternetAddress iAddress) {
            fromEmail = iAddress.getAddress();
            fromName = iAddress.getPersonal();
        }

        StringBuilder sb = new StringBuilder();
        for (Address recipient : parser.getRecipients()) {
            if (recipient instanceof InternetAddress internetAddress) {
                sb.append("/r/n").append(internetAddress.getAddress());
            }
        }
        String recipients = sb.delete(0, 4).toString();

        return """
            "report": "%b",
            "from_email": "%s",
            "from_name": "%s",
            "to_list": "%s",
            "subject": "%s",
            "date_received": "%s"
            "body_text": "%s",
            "body_html": "%s",
            """.formatted(report, fromEmail, fromName, recipients, parser.getSubject(), parser.getSentDate(),
            !report ? parser.getTextContent() : null,
            !report ? parser.getHtmlContent() : null);
    }


    private void sendAsyncRequest(URI uri, String jsonPayload) {
        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .uri(uri).header("Content-Type", "application/json").build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }

}
