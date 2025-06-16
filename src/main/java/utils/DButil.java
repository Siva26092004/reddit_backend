package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DButil {

    private static final String URL = "jdbc:mysql://localhost:3306/reddit_app";
    private static final String USER = "root";
    private static final String PASSWORD = "siva@zoho";

    // Static initializer block to load the JDBC driver when the class is loaded
    static {
        try {
            // Explicitly load the MySQL JDBC driver class
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            // If the driver class is not found, print an error and throw a runtime exception.
            // This prevents the application from proceeding if the driver is missing.
            System.err.println("Error: MySQL JDBC Driver not found. Please ensure the mysql-connector-java JAR is in your classpath.");
            e.printStackTrace(); // Print stack trace for debugging
            throw new RuntimeException("Failed to load MySQL JDBC driver", e);
        }
    }

    /**
     * Establishes a connection to the MySQL database.
     * @return A Connection object to the database.
     * @throws SQLException If a database access error occurs or the URL is null.
     */
    public static Connection getConnection() throws SQLException {
        // Attempt to establish a connection using the URL, username, and password.
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
