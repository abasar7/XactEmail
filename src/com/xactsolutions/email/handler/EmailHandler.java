package com.xactsolutions.email.handler;

import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.util.Utils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import static com.xactsolutions.email.util.Utils.isEmpty;

@Slf4j
public class EmailHandler extends BaseRequestHandler {

    private final Maddy maddy;

    public EmailHandler(String endpoint, Maddy maddy) {
        super(endpoint);
        this.maddy = maddy;
    }

    @Override
    protected @NonNull Response post() {
        String from = Utils.getJsonFieldValue(payloadStr, "from");
        String to = Utils.getJsonFieldValue(payloadStr, "to");
        String subject = Utils.getJsonFieldValue(payloadStr, "subject");
        String content = Utils.getJsonFieldValue(payloadStr, "content");
        if (isEmpty(from) || isEmpty(to) || isEmpty(subject) || isEmpty(content)) {
            return new JsonResponse(400, "{\"message\": \"Payload has empty value in from/to/subject/content\"}");
        }

        maddy.sendEmail(from, to, subject, content);
        log.info("Email send successfully to {}", to);
        return new JsonResponse(201, null);
    }

}
