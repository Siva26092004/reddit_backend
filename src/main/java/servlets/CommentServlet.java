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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.DButil; 
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 10,      // 10MB
    maxRequestSize = 1024 * 1024 * 50    // 50MB
)
public class CommentServlet extends HttpServlet {
    private Gson gson;
    @Override
    public void init() throws ServletException {
        super.init();
        gson = new GsonBuilder().setPrettyPrinting().create();
    }
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
            // Parse the incoming JSON string into a JsonObject
            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            
            System.out.println("DEBUG - CommentServlet doPost: Parsed JSON object: " + jsonObject.toString());
            // Validate and extract required fields
            if (!jsonObject.has("postId") || jsonObject.get("postId").isJsonNull()) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'postId' field in request.\"}");
                return;
            }
            if (!jsonObject.has("userId") || jsonObject.get("userId").isJsonNull()) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'userId' field in request.\"}");
                return;
            }
            if (!jsonObject.has("content") || jsonObject.get("content").isJsonNull()) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing 'content' field in request.\"}");
                return;
            }
            postId = jsonObject.get("postId").getAsInt();
            userId = jsonObject.get("userId").getAsInt();
            content = jsonObject.get("content").getAsString();
            // Extract optional parentCommentId if present and not null
            if (jsonObject.has("parentCommentId") && !jsonObject.get("parentCommentId").isJsonNull()) {
                parentCommentId = jsonObject.get("parentCommentId").getAsInt();
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
                out.print("{\"error\":\"Comment content cannot be empty.\"}\""); // Fixed extra quote
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
                    
                    out.print("{\"message\":\"Comment created successfully\", \"commentId\":" + commentId + "}");
                } else {
                    // If no rows were affected, insertion failed
                    res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print("{\"error\":\"Failed to insert comment into database.\"}");
                }
            } catch (SQLException e) {
                // Handle SQL errors during insertion
                System.err.println("Database error during comment insert: " + e.getMessage());
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                String errorMsg = e.getMessage().replace("\"", "\\\""); // Escape quotes for JSON response
                out.print("{\"error\":\"Database error: " + errorMsg + "\"}");
            }

        } catch (Exception e) {
            // Catch all other exceptions (e.g., JSON parsing errors, NumberFormatException)
            System.err.println("Error parsing JSON or creating comment: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for detailed debugging
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 for bad request data
            String errorMsg = e.getMessage().replace("\"", "\\\""); // Escape quotes for JSON response
            out.print("{\"error\":\"Invalid JSON format or missing fields: " + errorMsg + "\"}");
        }
    }

   
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();

        String postIdParam = req.getParameter("postId");

        // Validate postId parameter
        if (postIdParam == null || postIdParam.trim().isEmpty()) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Missing postId parameter.\"}"); // Removed extra quote
            return;
        }

        try (Connection conn = DButil.getConnection()) {
            int postId = Integer.parseInt(postIdParam);

            // SQL query to fetch all comments for a post, along with their vote counts
            // We fetch all comments and then build the tree structure in memory
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT c.id AS commentId, c.post_id, c.user_id, c.content, c.parent_comment_id, c.created_at AS commentCreatedAt, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = 1 THEN 1 ELSE 0 END), 0) AS upvote_count, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = -1 THEN 1 ELSE 0 END), 0) AS downvote_count ");
            sqlBuilder.append("FROM comments c ");
            sqlBuilder.append("LEFT JOIN votes v ON c.id = v.target_id AND v.target_type = 'comment' "); // Join with votes table for comments
            sqlBuilder.append("WHERE c.post_id = ? "); // Filter by post ID
            // Group by all non-aggregated columns to get correct counts per comment
            sqlBuilder.append("GROUP BY c.id, c.post_id, c.user_id, c.content, c.parent_comment_id, c.created_at ");
            sqlBuilder.append("ORDER BY c.created_at ASC"); // Order comments chronologically

            PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString());
            stmt.setInt(1, postId); // Set the postId parameter
            ResultSet rs = stmt.executeQuery();

            // Map to hold all comments by their ID for easy lookup during tree building
            Map<Integer, JsonObject> allCommentsMap = new HashMap<>();
            // List to hold only top-level comments for the final JSON array response
            List<JsonObject> topLevelComments = new ArrayList<>();

            // First pass: Populate allCommentsMap and identify top-level comments
            while (rs.next()) {
                JsonObject commentJson = new JsonObject();
                int commentId = rs.getInt("commentId");
                
                // Retrieve parent_comment_id and check for SQL NULL using wasNull()
                int currentParentCommentId = rs.getInt("parent_comment_id"); 
                boolean isParentCommentIdNull = rs.wasNull(); // wasNull() checks if the LAST read value was NULL

                commentJson.addProperty("id", commentId);
                commentJson.addProperty("post_id", rs.getInt("post_id"));
                commentJson.addProperty("user_id", rs.getInt("user_id"));
                commentJson.addProperty("content", rs.getString("content"));
                commentJson.addProperty("created_at", rs.getTimestamp("commentCreatedAt").toString());
                commentJson.addProperty("upvote_count", rs.getInt("upvote_count"));
                commentJson.addProperty("downvote_count", rs.getInt("downvote_count"));
                commentJson.add("replies", new JsonArray()); // Initialize an empty array for replies

                // Add parent_comment_id to JSON if it's not null
                if (!isParentCommentIdNull) {
                    commentJson.addProperty("parent_comment_id", currentParentCommentId);
                }

                allCommentsMap.put(commentId, commentJson); // Store comment in map for easy lookup

                // If no parent, it's a top-level comment
                if (isParentCommentIdNull) {
                    topLevelComments.add(commentJson);
                }
            }

            // Second pass: Build the nested tree structure
            // Iterate through all comments found and place replies into their parent's 'replies' array
            for (JsonObject comment : allCommentsMap.values()) {
                // Only process comments that have a parent_comment_id (i.e., are replies)
                if (comment.has("parent_comment_id")) {
                    int parentId = comment.get("parent_comment_id").getAsInt();
                    JsonObject parentComment = allCommentsMap.get(parentId);
                    // If the parent comment exists in our map, add this comment to its replies list
                    // (This handles cases where a reply's parent might also be a reply, forming deeper nesting)
                    if (parentComment != null) {
                        parentComment.getAsJsonArray("replies").add(comment);
                    }
                }
            }

            // Convert the list of top-level comments (which now contain nested replies) to a JsonArray
            JsonArray finalCommentsArray = new JsonArray();
            for (JsonObject comment : topLevelComments) {
                finalCommentsArray.add(comment);
            }
            
            // Send the final JSON response
            out.print(gson.toJson(finalCommentsArray));

        } catch (NumberFormatException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Invalid postId format.\"}"); // Removed extra quote
        } catch (SQLException e) {
            System.err.println("Database error in CommentServlet doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMsg = e.getMessage().replace("\"", "\\\"");
            out.print("{\"error\":\"Database error: " + errorMsg + "\"}");
        } catch (Exception e) {
            System.err.println("General error in CommentServlet doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMsg = e.getMessage().replace("\"", "\\\"");
            out.print("{\"error\":\"An unexpected error occurred: " + errorMsg + "\"}");
        }
    }
}
