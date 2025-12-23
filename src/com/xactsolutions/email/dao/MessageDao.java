package com.xactsolutions.email.dao;

import com.xactsolutions.email.model.MessageMeta;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MessageDao {

    public List<MessageMeta> listUnseenMessages() throws SQLException {
        List<MessageMeta> messages = new ArrayList<>();

        Connection conn = ConnectionManager.getConnection();
        var unseenSql = "SELECT mboxId, msgId, seen, extBodyKey, recent, date FROM msgs WHERE seen=0;";
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
        return messages;
    }

}
