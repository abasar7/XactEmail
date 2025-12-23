package com.xactsolutions.email;

import com.xactsolutions.email.dao.MessageDao;

import java.sql.SQLException;

public class XactEmail {


    static void main() throws SQLException {
        MessageDao messageDao = new MessageDao();
        messageDao.listUnseenMessages().forEach(System.out::println);
    }

}
