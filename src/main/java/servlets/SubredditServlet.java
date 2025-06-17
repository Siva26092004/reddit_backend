package servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import utils.DButil;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.sql.*;

public class SubredditServlet extends HttpServlet {
    
  /*   @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type");
        res.setStatus(HttpServletResponse.SC_OK);
    }*/
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
      //  res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();
        
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            
            // Parse JSON properly
            JSONObject jsonObject = new JSONObject(body);
            String name = jsonObject.getString("name");
            String description = jsonObject.getString("description");
            String created_by=jsonObject.getString("created_by");
            try (Connection conn = DButil.getConnection()) {
                String sql = "INSERT INTO subreddits (name, description,created_by) VALUES (?, ?,?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, name);
                stmt.setString(2, description);
                stmt.setInt(3,Integer.parseInt(created_by));
                int rows = stmt.executeUpdate();
                
                if (rows > 0) {
                    JSONObject response = new JSONObject();
                    response.put("message", "Subreddit created successfully");
                    out.print(response.toString());
                    System.out.println(response.toString());
                } else {
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Failed to create subreddit");
                    out.print(errorResponse.toString());
                }
            } catch (SQLException e) {
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", e.getMessage());
                out.print(errorResponse.toString());
            }
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid JSON format: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
       // res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();
        
        String subredditId = req.getParameter("id");
        System.out.println("Subreddit ID: " + subredditId + " called");
        
        try (Connection conn = DButil.getConnection()) { 
            if (subredditId != null && !subredditId.trim().isEmpty()) {
                // Get single subreddit
                String sql = "SELECT * FROM subreddits WHERE id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, Integer.parseInt(subredditId));
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    JSONObject subredditJson = new JSONObject();
                    subredditJson.put("id", rs.getInt("id"));
                    subredditJson.put("name", rs.getString("name"));
                    subredditJson.put("description", rs.getString("description"));
                    out.print(subredditJson.toString());
                } else {
                    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Subreddit not found");
                    out.print(errorResponse.toString());
                }
            } else {
                // Get all subreddits
                String sql = "SELECT * FROM subreddits";
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();
                
                JSONArray subredditsArray = new JSONArray();
                while (rs.next()) {
                    JSONObject subredditJson = new JSONObject();
                    subredditJson.put("id", rs.getInt("id"));
                    subredditJson.put("name", rs.getString("name"));
                    subredditJson.put("description", rs.getString("description"));
                    subredditsArray.put(subredditJson);
                }
                
                out.print(subredditsArray.toString());
            }
        } catch (NumberFormatException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid subreddit ID format");
            out.print(errorResponse.toString());
        } catch (SQLException e) {
            System.err.println("Database error in SubredditServlet doGet: " + e.getMessage());
            e.printStackTrace(); 
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Database error: " + e.getMessage());
            out.print(errorResponse.toString());
        } catch (Exception e) {
            System.err.println("General error in SubredditServlet doGet: " + e.getMessage());
            e.printStackTrace(); 
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An unexpected error occurred: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }
}
