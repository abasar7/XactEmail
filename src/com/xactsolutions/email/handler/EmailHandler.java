package com.xactsolutions.email.handler;

import com.xactsolutions.email.filter.AuthFilter;
import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.util.Utils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import static com.xactsolutions.email.util.Utils.isEmpty;

@Slf4j
public class EmailHandler extends BaseRequestHandler {

    private final Maddy maddy;

    public EmailHandler(String endpoint, AuthFilter authFilter, Maddy maddy) {
        super(endpoint, authFilter);
        this.maddy = maddy;
    }

    @Override
    protected @NonNull Response post() {
        String from = Utils.getJsonFieldValue(payloadStr, "from");
        String to = Utils.getJsonFieldValue(payloadStr, "to");
        String subject = Utils.getJsonFieldValue(payloadStr, "subject");
        String unsubscribeUrl = Utils.getJsonFieldValue(payloadStr, "unsubscribeUrl");
        String content = Utils.getJsonFieldValue(payloadStr, "content");
        log.trace("Extracted payload parts\n from: {}, to: {}, sub: {}, url: {}, content:\n{}", from, to, subject, unsubscribeUrl, content);
        if (isEmpty(from) || isEmpty(to) || isEmpty(subject) || isEmpty(content)) {
            return new JsonResponse(400, "{\"message\": \"Payload has empty value in from/to/subject/content\"}");
        }
        if (!isEmpty(unsubscribeUrl) && !unsubscribeUrl.startsWith("https://")) {
            return new JsonResponse(400, "{\"message\": \"Invalid unsubscribeUrl.\"}");
        }

        try {
            maddy.sendEmail(from, to, subject, content, unsubscribeUrl);
            log.debug("Email successfully sent to {} from {}", from, to);
        } catch (Exception e) {
            log.error("Error in sending email via maddy", e);
            return new JsonResponse(500, e.getMessage());
        }

        return new JsonResponse(202, null);
    }

}
