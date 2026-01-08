package com.xactsolutions.email;

import com.sun.net.httpserver.HttpServer;
import com.xactsolutions.email.handler.DomainHandler;
import com.xactsolutions.email.handler.EmailHandler;
import com.xactsolutions.email.handler.WebhookProcessEmailsHandler;
import com.xactsolutions.email.maddy.Maddy;
import com.xactsolutions.email.maddy.MaddyServiceHelper;
import com.xactsolutions.email.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class XactEmail {

    private static final String DOMAINS_URL = "/domains";
    private static final String EMAILS_URL = "/emails";
    private static final String WEBHOOKS_TRIGGER_IMAP_URL = "/webhooks/process-emails";


    static void main() throws IOException {
        Properties properties = loadProperties();
        Maddy maddy = new Maddy(properties.getProperty("maddy.username"),
            properties.getProperty("maddy.password"),
            properties.getProperty("maddy.imap.dburl"));

        int port = Integer.parseInt(properties.getProperty("server.port"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/health", exchange -> {
            log.debug("Received new {} request at /health.", exchange.getRequestMethod());
            boolean status = MaddyServiceHelper.getServiceStatus();
            HttpUtils.setResponse(status ? 200 : 500, null, exchange);
        });

        server.createContext(DOMAINS_URL, new DomainHandler(DOMAINS_URL, maddy));
        server.createContext(EMAILS_URL, new EmailHandler(EMAILS_URL, maddy));

        WebhookProcessEmailsHandler webhookProcessEmailsHandler = new WebhookProcessEmailsHandler(WEBHOOKS_TRIGGER_IMAP_URL, maddy,
            properties.getProperty("maddy.imap.message.dir"),
            properties.getProperty("crm.inbound.url"),
            properties.getProperty("crm.report.url"));
        server.createContext(WEBHOOKS_TRIGGER_IMAP_URL, webhookProcessEmailsHandler);

        server.start();

        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(webhookProcessEmailsHandler::triggerProcessing, 1, 5, TimeUnit.MINUTES);
    }


    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream is = XactEmail.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
            }
        }
        return properties;
    }

}
