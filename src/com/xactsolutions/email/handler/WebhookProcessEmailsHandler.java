package com.xactsolutions.email.handler;

import com.xactsolutions.email.exception.HtmlContentNotFoundException;
import com.xactsolutions.email.filter.AuthFilter;
import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.model.MessageMeta;
import com.xactsolutions.email.parser.EmailParser;
import jakarta.mail.Address;
import jakarta.mail.internet.InternetAddress;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xactsolutions.email.util.Utils.isEmpty;

@Slf4j
public class WebhookProcessEmailsHandler extends BaseRequestHandler {

    private final Maddy maddy;
    private final String messageDir;
    private final URI crmInboundUri;
    private final URI crmReportUri;
    private final HttpClient httpClient;

    private static LocalDateTime lastScannedTime = LocalDateTime.now();
    private static final AtomicBoolean scanning = new AtomicBoolean(false);

    public WebhookProcessEmailsHandler(String endpoint, AuthFilter authFilter, Maddy maddy, String messageDir, String crmInboundUrl, String crmReportUrl) {
        super(endpoint, authFilter);
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

                    Map<Long, List<Long>> regularIdMap = new HashMap<>();
                    Map<Long, List<Long>> reportIdMap = new HashMap<>();
                    int count = 0;
                    for (var meta : messages) {
                        long id = meta.getId();
                        long userId = meta.getUserId();
                        String bodyKey = meta.getBodyKey();
                        try {
                            EmailParser parser = new EmailParser(messageDir + bodyKey);
                            boolean report = parser.isReport();
                            if (report && parser.getReportSubType().equals("delivery-status")) {
                                EmailParser newParser = parser.extractOriginalReportedEmailHeader();
                                String crmPayload = buildCRMPayload(newParser, true);
                                forwardEmailData(crmReportUri, crmPayload);
                                reportIdMap.computeIfAbsent(userId, _ -> new ArrayList<>()).add(id);
                            } else if (!report) {
                                String crmPayload = buildCRMPayload(parser, false);
                                forwardEmailData(crmInboundUri, crmPayload);
                                regularIdMap.computeIfAbsent(userId, _ -> new ArrayList<>()).add(id);
                            }
                            count++;
                        } catch (Exception e) {
                            log.error("Failed to forward message {}_{} - {} to {}", userId, id, bodyKey, crmInboundUri, e);
                        }
                    }

                    log.debug("Total {}/{} messages has been forwarded (will be mark as soon).",
                        count, messages.size());
                    regularIdMap.forEach(maddy::markMessagesAsSeen);
                    reportIdMap.forEach(maddy::markMessagesAsSeen);
                } catch (Exception e) {
                    log.error("Error in fetching/updating IMAP messages from DB", e);
                } finally {
                    lastScannedTime = LocalDateTime.now();
                    scanning.set(false);
                }
            });

        return new JsonResponse(202, null);
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

        String txtContent = null, htmlContent = null;
        if (!report) {
            txtContent = parser.getTextContent();
            try { htmlContent = parser.getHtmlContent(); }
            catch (HtmlContentNotFoundException e) {/**/}

            if (!isEmpty(txtContent)) txtContent = txtContent.replace("\r", "\\r").replace("\n", "\\n").replace("\"", "\\\"");
            if (!isEmpty(htmlContent)) htmlContent = htmlContent.replace("\r", "\\r").replace("\n", "\\n").replace("\"", "\\\"");
        }

        return """
            {
                "report": %b,
                "from_email": "%s",
                "from_name": "%s",
                "to_list": "%s",
                "subject": "%s",
                "date_received": "%s",
                "body_text": "%s",
                "body_html": "%s"
            }
            """.formatted(report, fromEmail, fromName, recipients, parser.getSubject(), parser.getSentDate(), txtContent, htmlContent);
    }


    private void forwardEmailData(URI uri, String jsonPayload) throws IOException, InterruptedException {
        log.debug("Forwarding email data to {}", uri);
        log.trace("Email forward request {} with payload\n{}", uri, jsonPayload);

        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .uri(uri).header("Content-Type", "application/json").build();
        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

}
