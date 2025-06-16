package servlets;

import utils.DButil;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.json.JSONObject;

public class RegisterServlet extends HttpServlet {
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Access-Control-Allow-Origin","http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods","POST, GET, OPTIONS, DELETE");
        res.setHeader("Access-Control-Allow-Headers","Content-Type, Accept");
        res.setStatus(HttpServletResponse.SC_OK);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setHeader("Access-Control-Allow-Origin","http://localhost:4200");
        res.setHeader("Access-Control-Allow-Headers","Content-Type");
        res.setHeader("Access-Control-Allow-Methods","POST");
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
            String password = json.getString("password");
            
            // Get email if provided
            String email = null;
            if (json.has("email")) {
                email = json.getString("email");
            }
            
            System.out.println("Registration attempt: " + username + " / " + email);
            
            try (Connection conn = DButil.getConnection()) {
                // First check if username already exists
                String checkSql = "SELECT COUNT(*) FROM users WHERE username = ?";
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setString(1, username);
                ResultSet checkRs = checkStmt.executeQuery();
                
                if (checkRs.next() && checkRs.getInt(1) > 0) {
                    res.setStatus(409); // Conflict
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Username already exists");
                    res.getWriter().print(errorResponse.toString());
                    return;
                }
                
                // Generate salt and hash password
                String salt = generateSalt();
                String hashedPassword = hashPassword(password, salt);
                
                // Insert new user with hashed password
                String sql;
                PreparedStatement stmt;
                
                if (email != null && !email.trim().isEmpty()) {
                    sql = "INSERT INTO users (username, password_hash, salt, email) VALUES (?, ?, ?, ?)";
                    stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    stmt.setString(1, username);
                    stmt.setString(2, hashedPassword);
                    stmt.setString(3, salt);
                    stmt.setString(4, email);
                } else {
                    sql = "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)";
                    stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    stmt.setString(1, username);
                    stmt.setString(2, hashedPassword);
                    stmt.setString(3, salt);
                }
                
                int rows = stmt.executeUpdate();
                PrintWriter out = res.getWriter();
                
                if (rows > 0) {
                    // Get the generated user ID
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    int userId = -1;
                    if (generatedKeys.next()) {
                        userId = generatedKeys.getInt(1);
                    }
                    
                    // Create user object for response
                    JSONObject userObj = new JSONObject();
                    userObj.put("id", userId);
                    userObj.put("username", username);
                    if (email != null && !email.trim().isEmpty()) {
                        userObj.put("email", email);
                    } else {
                        userObj.put("email", username + "@example.com");
                    }
                    
                    // Create response object
                    JSONObject response = new JSONObject();
                    response.put("message", "Registration successful");
                    response.put("user", userObj);
                    
                    System.out.println("Registration successful for user: " + userObj.toString());
                    out.print(response.toString());
                    
                } else {
                    res.setStatus(500);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Registration failed");
                    out.print(errorResponse.toString());
                }
                
            } catch (SQLException e) {
                System.err.println("Database error during registration: " + e.getMessage());
                e.printStackTrace();
                res.setStatus(500);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error: " + e.getMessage());
                res.getWriter().print(errorResponse.toString());
            }
            
        } catch (Exception e) {
            System.err.println("General error during registration: " + e.getMessage());
            e.printStackTrace();
            res.setStatus(500);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Server error: " + e.getMessage());
            res.getWriter().print(errorResponse.toString());
        }
    }
    
    /**
     * Generates a random salt for password hashing
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Hashes a password with the given salt using SHA-256
     */
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
}
