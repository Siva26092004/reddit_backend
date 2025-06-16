 import java.sql.*;

public class Test {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/reddit_app";
        String user = "root";
        String password = "siva@zoho"; // your MySQL password

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println(" Connected successfully!");
        } catch (SQLException e) {
            System.out.println(" Connection failed!");
            e.printStackTrace();
        }
    }
}
