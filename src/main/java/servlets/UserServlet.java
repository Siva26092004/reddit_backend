package servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import org.json.JSONObject;
import org.json.JSONArray;
import utils.DButil;

public class UserServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();
        
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Handle basic user info request with userId parameter
                String userIdParam = req.getParameter("userId");
                if (userIdParam != null) {
                    int userId = Integer.parseInt(userIdParam);
                    getUserInfo(userId, out, res);
                } else {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Missing userId parameter");
                    out.print(errorResponse.toString());
                }
            } else {
                // Handle path-based requests like /users/10/subreddits or /users/10/posts
                String[] pathParts = pathInfo.split("/");
                
                if (pathParts.length >= 3) {
                    int userId = Integer.parseInt(pathParts[1]);
                    String resource = pathParts[2];
                    
                    switch (resource) {
                        case "subreddits":
                            getUserSubreddits(userId, out, res);
                            break;
                        case "posts":
                            getUserPosts(userId, out, res);
                            break;
                        default:
                            getUserInfo(userId, out, res);
                            break;
                    }
                } else if (pathParts.length == 2) {
                    // Handle /users/10 format
                    int userId = Integer.parseInt(pathParts[1]);
                    getUserInfo(userId, out, res);
                } else {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Invalid request path");
                    out.print(errorResponse.toString());
                }
            }
        } catch (NumberFormatException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid user ID format");
            out.print(errorResponse.toString());
        } catch (Exception e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            out.print(errorResponse.toString());
            e.printStackTrace();
        }
    }
    
    private void getUserInfo(int userId, PrintWriter out, HttpServletResponse res) {
        try (Connection conn = DButil.getConnection()) {
            String sql = "SELECT id, username, email, created_at FROM users WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                JSONObject userJson = new JSONObject();
                userJson.put("id", rs.getInt("id"));
                userJson.put("username", rs.getString("username"));
                userJson.put("email", rs.getString("email"));
                userJson.put("created_at", rs.getTimestamp("created_at").toString());
                out.print(userJson.toString());
            } else {
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "User not found");
                out.print(errorResponse.toString());
            }
        } catch (SQLException e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Database error: " + e.getMessage());
            out.print(errorResponse.toString());
            e.printStackTrace();
        }
    }
    
    private void getUserSubreddits(int userId, PrintWriter out, HttpServletResponse res) {
        try (Connection conn = DButil.getConnection()) {
            // Assuming you have a table that tracks user-subreddit relationships
            // This could be subreddit memberships or subreddits created by the user
            String sql = "SELECT s.id, s.name, s.description, s.created_at " +
                        "FROM subreddits s " +
                        "WHERE s.created_by = ? " +
                        "ORDER BY s.created_at DESC";
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            JSONArray subredditsArray = new JSONArray();
            while (rs.next()) {
                JSONObject subredditJson = new JSONObject();
                subredditJson.put("id", rs.getInt("id"));
                subredditJson.put("name", rs.getString("name"));
                subredditJson.put("description", rs.getString("description"));
                subredditJson.put("created_at", rs.getTimestamp("created_at").toString());
                subredditsArray.put(subredditJson);
            }
            
            JSONObject response = new JSONObject();
            response.put("subreddits", subredditsArray);
            response.put("count", subredditsArray.length());
            out.print(response.toString());
            
        } catch (SQLException e) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Database error: " + e.getMessage());
            out.print(errorResponse.toString());
            e.printStackTrace();
        }
    }
    
   private void getUserPosts(int userId, PrintWriter out, HttpServletResponse res) {
    try (Connection conn = DButil.getConnection()) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT p.id AS postId, p.user_id, p.subreddit_id, p.title, p.content, p.created_at AS postCreatedAt, ");
        sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = 1 THEN 1 ELSE 0 END), 0) AS upvote_count, ");
        sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = -1 THEN 1 ELSE 0 END), 0) AS downvote_count, ");
        sqlBuilder.append("sm.id AS mediaId, sm.media_url, sm.media_type, sm.created_at AS mediaCreatedAt ");
        sqlBuilder.append("FROM posts p ");
        sqlBuilder.append("LEFT JOIN post_media sm ON p.id = sm.post_id ");
        sqlBuilder.append("LEFT JOIN votes v ON p.id = v.target_id AND v.target_type = 'post' ");
        sqlBuilder.append("WHERE p.user_id = ? ");
        sqlBuilder.append("GROUP BY p.id, p.user_id, p.subreddit_id, p.title, p.content, p.created_at, sm.id, sm.media_url, sm.media_type, sm.created_at ");
        sqlBuilder.append("ORDER BY p.created_at DESC, sm.id ASC");
        
        PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString());
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();
        
        JSONArray postsArray = new JSONArray();
        while (rs.next()) {
            JSONObject postJson = new JSONObject();
            postJson.put("id", rs.getInt("postId"));
            postJson.put("user_id", rs.getInt("user_id"));
            postJson.put("subreddit_id", rs.getInt("subreddit_id"));
            postJson.put("title", rs.getString("title"));
            postJson.put("content", rs.getString("content"));
            postJson.put("created_at", rs.getTimestamp("postCreatedAt").toString());
            postJson.put("upvote_count", rs.getInt("upvote_count"));
            postJson.put("downvote_count", rs.getInt("downvote_count"));
            
            // Handle media data (can be null)
            if (rs.getObject("mediaId") != null) {
                JSONObject mediaJson = new JSONObject();
                mediaJson.put("id", rs.getInt("mediaId"));
                mediaJson.put("media_url", rs.getString("media_url"));
                mediaJson.put("media_type", rs.getString("media_type"));
                mediaJson.put("created_at", rs.getTimestamp("mediaCreatedAt").toString());
                postJson.put("media", mediaJson);
            } else {
                postJson.put("media", JSONObject.NULL);
            }
            
            postsArray.put(postJson);
        }
        
        JSONObject response = new JSONObject();
        response.put("posts", postsArray);
        response.put("count", postsArray.length());
        out.print(response.toString());
        
    } catch (SQLException e) {
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error", "Database error: " + e.getMessage());
        out.print(errorResponse.toString());
        e.printStackTrace();
    }
}

}
