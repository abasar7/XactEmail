package com.xactsolutions.email.handler;

import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.util.Utils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DomainHandler extends BaseRequestHandler {

    private final Maddy maddy;

    public DomainHandler(String endpoint, Maddy maddy) {
        super(endpoint);
        this.maddy = maddy;
    }

    @Override
    protected @NonNull Response getOne() {
        String domainKey = maddy.resolveDomainKey(key);
        String responseStr = "{\"dnsRecordType\": \"TXT\", \"hostname\": \"default._domainkey.%s\", \"value\": \"%s\"}".formatted(key, domainKey);
        return new Response(200, responseStr);
    }

    @Override
    protected @NonNull Response post() {
        String name = Utils.getJsonFieldValue(payloadStr, "name");
        String domainKey = maddy.addDomain(name);

        String responseStr = "{\"dnsRecordType\": \"TXT\", \"hostname\": \"default._domainkey.%s\", \"value\": \"%s\"}".formatted(name, domainKey);
        return new Response(201, responseStr);
    }

    @Override
    protected @NonNull Response delete() {
        maddy.removeDomain(key);
        return new Response(201, null);
    }

}
