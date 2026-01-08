package com.xactsolutions.email.maddy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class ConnectionManager {

    private static String dbUrl = null;

    private static Connection connection;

    protected static void setDbUrl(String dbUrl) {
        ConnectionManager.dbUrl = dbUrl;
    }

    protected static Connection getConnection() throws SQLException {
        if (dbUrl == null) throw new RuntimeException("dbUrl should be set before using a connection.");
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl);
        }
        return connection;
    }

}
