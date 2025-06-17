package servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.json.JSONObject;
import utils.DButil;

public class LoginServlet extends HttpServlet {
   /* @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Access-Control-Allow-Origin","http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods","POST, GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers","Content-Type");
        res.setStatus(HttpServletResponse.SC_OK);
    }*/
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
      //  res.setHeader("Access-Control-Allow-Origin","http://localhost:4200");
      //  res.setHeader("Access-Control-Allow-Methods","POST");
      //  res.setHeader("Access-Control-Allow-Headers","Content-Type");
        res.setContentType("application/json");
        BufferedReader reader = req.getReader();
        StringBuilder jsonBody = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBody.append(line);
        }
        try {
            JSONObject json = new JSONObject(jsonBody.toString());
            String username = json.getString("username");
            String password1 = json.getString("password");
            System.out.println("Login attempt: " + username);  
            try (Connection conn = DButil.getConnection()) {
                String sql = "SELECT id, username, email, created_at, password_hash, salt FROM users WHERE username = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                PrintWriter out = res.getWriter();
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    if (verifyPassword(password1, storedHash, salt)) {
                        JSONObject userObj = new JSONObject();
                        userObj.put("id", rs.getInt("id"));
                        userObj.put("username", rs.getString("username"));
                        String email = rs.getString("email");
                        if (email != null) {
                            userObj.put("email", email);
                        } else {
                            userObj.put("email", username + "@example.com"); // Default email if null
                        }
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        if (createdAt != null) {
                            userObj.put("created_at", createdAt.toString());
                        }
                        JSONObject response = new JSONObject();
                        response.put("message", "Login successful");
                        response.put("user", userObj);
                        System.out.println("Login successful for user: " + userObj.toString());
                        out.print(response.toString());
                    } else {
                        res.setStatus(401);
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("error", "Invalid credentials");
                        out.print(errorResponse.toString());
                    }
                } else {
                    res.setStatus(401);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Invalid credentials");
                    out.print(errorResponse.toString());
                }
            } catch (SQLException e) {
                System.err.println("Database error during login: " + e.getMessage());
                e.printStackTrace();
                res.setStatus(500);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error: " + e.getMessage());
                res.getWriter().print(errorResponse.toString());
            }
        } catch (Exception e) {
            System.err.println("General error during login: " + e.getMessage());
            e.printStackTrace();
            res.setStatus(500);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Server error: " + e.getMessage());
            res.getWriter().print(errorResponse.toString());
        }
    }
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    public static boolean verifyPassword(String password, String storedHash, String salt) {
        String hashedPassword = hashPassword(password, salt);
        return hashedPassword.equals(storedHash);
    }
}
