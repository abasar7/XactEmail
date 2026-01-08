package com.xactsolutions.email.maddy;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Slf4j
public class MaddyDomainHelper {

    private static final Path DKIM_DIR = Path.of("/var/lib/maddy/dkim_keys");
    private static final String DKIM_DNS_SUFFIX = "_default.dns";
    private static final String DKIM_KEY_SUFFIX = "_default.key";
    private static final Path CONFIG_PATH = Path.of("/etc/maddy/maddy.conf");
    private static final String DOMAIN_LINE = "$(local_domains) = $(primary_domain) ";


    public static synchronized String addDomain(String domain) {
        updateDomainConfig(domain, false);

        try {
            MaddyServiceHelper.restartService();
        } catch (RuntimeException e) {
            updateDomainConfig(domain, true);
            throw e;
        }

        return resolveDomainKey(domain);
    }

    public static synchronized void removeDomain(String domain) {
        updateDomainConfig(domain, true);

        try {
            MaddyServiceHelper.restartService();
        } catch (RuntimeException e) {
            updateDomainConfig(domain, false);
            throw e;
        }

        removeDomainKey(domain);
    }

    public static String resolveDomainKey(String domain) {
        try {
            return Files.readString(DKIM_DIR.resolve(domain + DKIM_DNS_SUFFIX));
        } catch (IOException e) {
            log.trace("Couldn't resolve DKIM key for domain ({})", domain, e);
            return "";
        }
    }

    private static void removeDomainKey(String domain) {
        try {
            Path dnsPath = DKIM_DIR.resolve(domain + DKIM_DNS_SUFFIX);
            Files.delete(dnsPath);
            Path keyPath = DKIM_DIR.resolve(domain + DKIM_KEY_SUFFIX);
            Files.delete(keyPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove DKIM key for domain " + domain, e);
        }
    }

    private static void updateDomainConfig(String domain, boolean delete) {
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(DOMAIN_LINE)) {
                    String[] split = lines.get(i).substring(DOMAIN_LINE.length()).split(" ");
                    ArrayList<String> domains = new ArrayList<>(Arrays.asList(split));
                    if (delete && domains.contains(domain)) domains.remove(domain);
                    else if (!delete && !domains.contains(domain)) domains.add(domain);
                    lines.set(i, DOMAIN_LINE + String.join(" ", domains));
                }
            }
            Files.write(CONFIG_PATH, lines);
            log.trace("Domain ({}) added to maddy config", domain);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read/write maddy config file", e);
        }
    }

}
