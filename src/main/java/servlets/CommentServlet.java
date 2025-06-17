package servlets;

import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig; 
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;
import utils.DButil; 

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 10,      // 10MB
    maxRequestSize = 1024 * 1024 * 50    // 50MB
)
public class CommentServlet extends HttpServlet {
    
    /*@Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200"); 
        res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS"); 
        res.setHeader("Access-Control-Allow-Headers", "Content-Type"); 
        res.setStatus(HttpServletResponse.SC_OK); 
    }*/
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        //res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json"); 
        PrintWriter out = res.getWriter();

        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String body = sb.toString();

        int postId = -1;
        int userId = -1;
        String content = null;
        Integer parentCommentId = null; 

        try {
            // Parse the incoming JSON string into a JSONObject
            JSONObject jsonObject = new JSONObject(body);
            
            System.out.println("DEBUG - CommentServlet doPost: Parsed JSON object: " + jsonObject.toString());
            
            // Validate and extract required fields
            if (!jsonObject.has("postId") || jsonObject.isNull("postId")) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'postId' field in request.\"}");
                return;
            }
            if (!jsonObject.has("userId") || jsonObject.isNull("userId")) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'userId' field in request.\"}");
                return;
            }
            if (!jsonObject.has("content") || jsonObject.isNull("content")) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'content' field in request.\"}");
                return;
            }
            
            postId = jsonObject.getInt("postId");
            userId = jsonObject.getInt("userId");
            content = jsonObject.getString("content");
            
            
            if (jsonObject.has("parentCommentId") && !jsonObject.isNull("parentCommentId")) {
                parentCommentId = jsonObject.getInt("parentCommentId");
            }

            // Additional validation for extracted values
            if (postId <= 0) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Invalid postId: must be a positive integer.\"}");
                return;
            }
            if (userId <= 0) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Invalid userId: must be a positive integer.\"}");
                return;
            }
            if (content == null || content.trim().isEmpty()) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Comment content cannot be empty.\"}");
                return;
            }
            
            System.out.println("DEBUG - CommentServlet doPost: Extracted values - postId: " + postId + ", userId: " + userId + ", content: '" + content + "', parentCommentId: " + parentCommentId);
            
            // Database insertion
            try (Connection conn = DButil.getConnection()) {
                String sql = "INSERT INTO comments (post_id, user_id, content, parent_comment_id, created_at) VALUES (?, ?, ?, ?, NOW())";
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, postId);
                stmt.setInt(2, userId);
                stmt.setString(3, content);
                if (parentCommentId != null) {
                    stmt.setInt(4, parentCommentId); // Set parent_comment_id if it's a reply
                } else {
                    stmt.setNull(4, java.sql.Types.INTEGER); // Set as NULL for top-level comments
                }
                
                int rows = stmt.executeUpdate(); // Execute the insert statement

                if (rows > 0) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    int commentId = -1;
                    if (generatedKeys.next()) {
                        commentId = generatedKeys.getInt(1); 
                    }
                    
                    JSONObject response = new JSONObject();
                    response.put("message", "Comment created successfully");
                    response.put("commentId", commentId);
                    out.print(response.toString());
                } else {
                    // If no rows were affected, insertion failed
                    res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\":\"Failed to insert comment into database.\"}");
                }
            } catch (SQLException e) {
                // Handle SQL errors during insertion
                System.err.println("Database error during comment insert: " + e.getMessage());
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error: " + e.getMessage());
                out.print(errorResponse.toString());
            }

        } catch (Exception e) {
            // Catch all other exceptions (e.g., JSON parsing errors, NumberFormatException)
            System.err.println("Error parsing JSON or creating comment: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for detailed debugging
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 for bad request data
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid JSON format or missing fields: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
       // res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();

        String postIdParam = req.getParameter("postId");

        // Validate postId parameter
        if (postIdParam == null || postIdParam.trim().isEmpty()) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Missing postId parameter.\"}");
            return;
        }

        try (Connection conn = DButil.getConnection()) {
            int postId = Integer.parseInt(postIdParam);

            // SQL query to fetch all comments for a post, along with their vote counts
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT c.id AS commentId, c.post_id, c.user_id, c.content, c.parent_comment_id, c.created_at AS commentCreatedAt, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = 1 THEN 1 ELSE 0 END), 0) AS upvote_count, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = -1 THEN 1 ELSE 0 END), 0) AS downvote_count ");
            sqlBuilder.append("FROM comments c ");
            sqlBuilder.append("LEFT JOIN votes v ON c.id = v.target_id AND v.target_type = 'comment' ");
            sqlBuilder.append("WHERE c.post_id = ? ");
            sqlBuilder.append("GROUP BY c.id, c.post_id, c.user_id, c.content, c.parent_comment_id, c.created_at ");
            sqlBuilder.append("ORDER BY c.created_at ASC");

            PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString());
            stmt.setInt(1, postId);
            ResultSet rs = stmt.executeQuery();

            // Map to hold all comments by their ID for easy lookup during tree building
            Map<Integer, JSONObject> allCommentsMap = new HashMap<>();
            // List to hold only top-level comments for the final JSON array response
            List<JSONObject> topLevelComments = new ArrayList<>();

            // First pass: Populate allCommentsMap and identify top-level comments
            while (rs.next()) {
                JSONObject commentJson = new JSONObject();
                int commentId = rs.getInt("commentId");
                
                // Retrieve parent_comment_id and check for SQL NULL using wasNull()
                int currentParentCommentId = rs.getInt("parent_comment_id"); 
                boolean isParentCommentIdNull = rs.wasNull();

                commentJson.put("id", commentId);
                commentJson.put("post_id", rs.getInt("post_id"));
                commentJson.put("user_id", rs.getInt("user_id"));
                commentJson.put("content", rs.getString("content"));
                commentJson.put("created_at", rs.getTimestamp("commentCreatedAt").toString());
                commentJson.put("upvote_count", rs.getInt("upvote_count"));
                commentJson.put("downvote_count", rs.getInt("downvote_count"));
                commentJson.put("replies", new JSONArray()); // Initialize an empty array for replies

                // Add parent_comment_id to JSON if it's not null
                if (!isParentCommentIdNull) {
                    commentJson.put("parent_comment_id", currentParentCommentId);
                }

                allCommentsMap.put(commentId, commentJson);

                // If no parent, it's a top-level comment
                if (isParentCommentIdNull) {
                    topLevelComments.add(commentJson);
                }
            }

            // Second pass: Build the nested tree structure
            for (JSONObject comment : allCommentsMap.values()) {
                // Only process comments that have a parent_comment_id (i.e., are replies)
                if (comment.has("parent_comment_id")) {
                    int parentId = comment.getInt("parent_comment_id");
                    JSONObject parentComment = allCommentsMap.get(parentId);
                    // If the parent comment exists in our map, add this comment to its replies list
                    if (parentComment != null) {
                        parentComment.getJSONArray("replies").put(comment);
                    }
                }
            }

            // Convert the list of top-level comments to a JSONArray
            JSONArray finalCommentsArray = new JSONArray();
            for (JSONObject comment : topLevelComments) {
                finalCommentsArray.put(comment);
            }
            
            // Send the final JSON response
            out.print(finalCommentsArray.toString());

        } catch (NumberFormatException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Invalid postId format.\"}");
        } catch (SQLException e) {
            System.err.println("Database error in CommentServlet doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Database error: " + e.getMessage());
            out.print(errorResponse.toString());
        } catch (Exception e) {
            System.err.println("General error in CommentServlet doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An unexpected error occurred: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }
}
