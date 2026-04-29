package com.xactsolutions.email.handler;

import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.util.SystemUtils;
import com.xactsolutions.email.util.Utils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DomainHandler extends BaseRequestHandler {

    private static final String DKIM_NAME_PREFIX = "default._domainkey.";
    private static final String DMARC_NAME_PREFIX = "_dmarc.";

    private final String mxIp;
    private final Maddy maddy;

    public DomainHandler(String endpoint, String mxIp, Maddy maddy) {
        super(endpoint);
        this.mxIp = mxIp;
        this.maddy = maddy;
    }

    @Override
    protected @NonNull Response getOne() {
        String domainKey = maddy.resolveDomainKey(key);
        boolean[] dnsStatus = getDnsStatus(domainKey);

        String responseStr = """
            {
                "dnsRecordType": "TXT",
                "hostname": "%s%s",
                "mxVerified": %b,
                "spfVerified": %b,
                "dkimVerified": %b,
                "dmarcVerified": %b,
                "domainKey": "%s"
            }
            """.formatted(DKIM_NAME_PREFIX, key, dnsStatus[0], dnsStatus[1], dnsStatus[2], dnsStatus[3], domainKey);
        return new JsonResponse(200, responseStr);
    }

    @Override
    protected @NonNull Response post() {
        String name = Utils.getJsonFieldValue(payloadStr, "name");
        String domainKey = maddy.addDomain(name);

        String responseStr = "{\"dnsRecordType\": \"TXT\", \"hostname\": \"%s%s\", \"value\": \"%s\"}".formatted(name, DKIM_NAME_PREFIX, domainKey);
        return new JsonResponse(201, responseStr);
    }

    @Override
    protected @NonNull Response delete() {
        maddy.removeDomain(key);
        return new JsonResponse(200, null);
    }

    private boolean[] getDnsStatus(String expectedDomainKey) {
        List<String> mxRecords = SystemUtils.resolveDnsRecord(key, "MX");
        log.trace("Resolved MX records({}): {}", key, mxRecords);
        boolean mxMatch = mxRecords.contains(mxIp);

        List<String> spfRecords = SystemUtils.resolveDnsSpfRecord(key);
        log.trace("Resolved SPF records({}): {}", key, spfRecords);
        boolean spfMatch = spfRecords.contains(mxIp) || (spfRecords.contains("mx") && mxMatch);

        List<String> dkimRecords = SystemUtils.resolveDnsRecord(DKIM_NAME_PREFIX+key, "TXT");
        log.trace("Resolved DKIM records({}): {}", key, dkimRecords);
        boolean dkimMatch = !dkimRecords.isEmpty() && dkimRecords.getFirst().equals(expectedDomainKey);

        String expectedDmarc = "v=DMARC1;p=none;" + key;
        List<String> dmarcRecords = SystemUtils.resolveDnsRecord(DMARC_NAME_PREFIX+key, "TXT");
        boolean dmarc = dmarcRecords.getFirst().replaceAll("\\s", "").equalsIgnoreCase(expectedDmarc);

        return new boolean[]{mxMatch, spfMatch, dkimMatch, dmarc};
    }

}
