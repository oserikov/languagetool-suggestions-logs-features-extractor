package io.github.oserikov.languagetool;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;

@Slf4j
public class DBUtils {
    public static ResultSet getRs(Statement stmt, String query) throws SQLException {
        log.info("Creating ResultSet. ...");
        ResultSet resultSet = stmt.executeQuery(query);
        log.info("Creating ResultSet. Done!");
        return resultSet;
    }

    public static Statement getStatement(Connection conn) throws SQLException {
        log.info("Creating Statement. ...");
        Statement statement = conn.createStatement();
        log.info("Creating Statement. Done!");
        return statement;
    }

    public static Connection getConnection(String dbUrl, String dbUser, String dbPassword) throws SQLException {
        log.info("Creating Connection. ...");
        Connection connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        log.info("Creating Connection. Done!");
        return connection;
    }
}
