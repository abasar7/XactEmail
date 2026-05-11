package com.xactsolutions.email.maddy;

import com.xactsolutions.email.model.MessageMeta;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.xactsolutions.email.util.Utils.isEmpty;

@Slf4j
public class Maddy {

    private static final Pattern DOMAIN_REGEX = Pattern.compile("^([a-zA-Z0-9]+(-[a-zA-Z0-9]+)*\\.)+[a-zA-Z]{2,}$");
    private static final String BODY_TAG = "</body>";

    @Getter
    private final String host;
    @Getter
    private final String ip;
    private final String password;

    public Maddy(String ip, String host, String password, String imapDbUrl) {
        this.ip = ip;
        this.host = host;
        this.password = password;
        ConnectionManager.setDbUrl(imapDbUrl);
    }

    public String addDomain(String domain) {
        if (isEmpty(domain)) throw new RuntimeException("Empty domain name provided");
        if (!DOMAIN_REGEX.matcher(domain).matches()) throw new RuntimeException("Invalid domain name format!!");
        domain = domain.toLowerCase();

        String domainKey = MaddyDomainHelper.addDomain(domain, password);
        MaddyServiceHelper.createAcc("crm@"+domain, password);
        return domainKey;
    }

    public void removeDomain(String domain) {
        if (isEmpty(domain)) throw new RuntimeException("Empty domain name provided");
        domain = domain.toLowerCase();

        MaddyDomainHelper.removeDomain(domain);
        MaddyServiceHelper.removeAcc("crm@"+domain);
    }

    public String resolveDomainKey(String domain) {
        if (isEmpty(domain)) throw new RuntimeException("Empty domain name provided");
        domain = domain.toLowerCase();

        return MaddyDomainHelper.resolveDomainKey(domain);
    }

    public void sendEmail(@NonNull String from, @NonNull String to, @NonNull String subject, @NonNull String htmlContent, String unsubscribeUrl) {
        String domain = from.substring(from.lastIndexOf('@') + 1);
        if (domain.endsWith(">")) domain = domain.substring(0, domain.length() - 1);
        String finalDomain = domain.toLowerCase();

        log.trace("Creating new session for domain {}", finalDomain);
        Session session = Session.getDefaultInstance(getSessionProperties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("crm@"+ finalDomain, password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(from);
            message.setRecipients(Message.RecipientType.TO, to);
            if (!isEmpty(unsubscribeUrl)) {
                String unsubscribeText = "<div style=\"text-align:center; padding: 4px;\">If you do not wish to receive any further communications, please <a href=\""+unsubscribeUrl+"\">unsubscribe here</a>.</div>\n";
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                message.setHeader("List-Unsubscribe", "<"+unsubscribeUrl+">,<mailto:unsubscribe@"+ domain +"?subject=unsubscribe>");

                StringBuilder sb = new StringBuilder(htmlContent);
                int bodyTagIndex = sb.indexOf(BODY_TAG);
                if (bodyTagIndex == -1) bodyTagIndex = sb.length();
                htmlContent = sb.insert(bodyTagIndex, unsubscribeText).toString();
            } else {
                message.setHeader("List-Unsubscribe", "<mailto:unsubscribe@"+ domain +"?subject=unsubscribe>");
            }
            message.setSubject(subject);
            message.setText(htmlContent, "utf-8", "html");
            Transport.send(message);
            log.debug("Email send successfully to {} from {}", to, from);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MessageMeta> listUnseenMessages() {
        List<MessageMeta> messages = new ArrayList<>();

        var unseenSql = "SELECT mboxId, msgId, seen, extBodyKey, recent, date FROM msgs WHERE seen=0;";
        try {
            var conn = ConnectionManager.getConnection();
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery(unseenSql);
            while (rs.next()) {
                MessageMeta meta = new MessageMeta();
                meta.setId(rs.getLong("msgId"));
                meta.setUserId(rs.getLong("mboxId"));
                meta.setBodyKey(rs.getString("extBodyKey"));
                meta.setSeen(rs.getBoolean("seen"));
                meta.setRecent(rs.getBoolean("recent"));
                meta.setDate(Instant.ofEpochSecond(rs.getLong("date")));
                messages.add(meta);
            }
        } catch (SQLException e) {
            log.error("Failed to fetch unseen messages. SQL: {}", unseenSql, e);
            throw new RuntimeException("SQLException: " + e);
        }
        return messages;
    }

    public void markMessagesAsSeen(long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return;

        var messageIdsStr = messageIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        var markSeenSql = "UPDATE msgs SET seen = 1 WHERE mboxId = "+userId+" AND msgId IN ("+ messageIdsStr +");";
        int updateStatus;
        try {
            var conn = ConnectionManager.getConnection();
            var stmt = conn.createStatement();
            updateStatus = stmt.executeUpdate(markSeenSql);
        } catch (SQLException e) {
            log.error("Failed to mark as seen messages. SQL: {}", markSeenSql, e);
            throw new RuntimeException("SQLException: " + e);
        }

        log.trace("{}/{} messages has been marked as seen", updateStatus, messageIds.size());
        if (updateStatus == 0) {
            log.warn("0 row updated when marking message as seen. Query was {}", markSeenSql);
        }
    }

    private Properties getSessionProperties() {
        Properties prop = new Properties();
        prop.put("mail.smtp.host", host);
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.starttls.enable", true);
        prop.put("mail.smtp.auth", true);
        return prop;
    }

}
