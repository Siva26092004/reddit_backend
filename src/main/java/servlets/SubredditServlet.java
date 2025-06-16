package servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import utils.DButil;
import java.io.*;
import java.sql.*;

public class SubredditServlet extends HttpServlet {
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type");
        res.setStatus(HttpServletResponse.SC_OK);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String body = sb.toString();
        String name = body.split("\"name\":\"")[1].split("\"")[0];
        String description = body.split("\"description\":\"")[1].split("\"")[0];
        try (Connection conn =DButil.getConnection()) {
            String sql = "INSERT INTO subreddits (name, description) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setString(2, description);
            int rows = stmt.executeUpdate();
            res.setContentType("application/json");
            PrintWriter out = res.getWriter();
            if (rows > 0) {
                out.print("{\"message\":\"Subreddit created successfully\"}");
            } else {
                out.print("{\"error\":\"Failed to create subreddit\"}");
            }
        } catch (SQLException e) {
            res.setStatus(500);
            res.getWriter().print("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
       res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
    res.setContentType("application/json");
    PrintWriter out = res.getWriter();
    String subredditId = req.getParameter("id"); 
    try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/reddit_app", "root", "siva@zoho")) {
        StringBuilder json = new StringBuilder();
        if (subredditId != null) {
            String sql = "SELECT * FROM subreddits WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, Integer.parseInt(subredditId));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"name\":\"").append(rs.getString("name")).append("\",")
                    .append("\"description\":\"").append(rs.getString("description")).append("\"")
                    .append("}");
            } else {
                res.setStatus(404);
                out.print("{\"error\":\"Subreddit not found\"}");
                return;
            }
        } else {
            String sql = "SELECT * FROM subreddits";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            json.append("[");
            while (rs.next()) {
                json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"name\":\"").append(rs.getString("name")).append("\",")
                    .append("\"description\":\"").append(rs.getString("description")).append("\"")
                    .append("},");
            }

            if (json.charAt(json.length() - 1) == ',') {
                json.deleteCharAt(json.length() - 1);
            }
            json.append("]");
        }

        out.print(json.toString());

    } catch (SQLException e) {
        res.setStatus(500);
        out.print("{\"error\":\"" + e.getMessage() + "\"}");
    }
}
}