package servlets;

import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import utils.DButil;

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 10,      // 10MB
    maxRequestSize = 1024 * 1024 * 50    // 50MB
)
public class PostServlet extends HttpServlet {
    private Cloudinary cloudinary;

    @Override
    public void init() throws ServletException {
        super.init();
        String cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
        String apiKey = System.getenv("CLOUDINARY_API_KEY");
        String apiSecret = System.getenv("CLOUDINARY_API_SECRET");
        
        Map config = new HashMap();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        cloudinary = new Cloudinary(config);
    }

   /* @Override
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
        
        String title = null;
        String content = null;
        int userId = -1;
        int subredditId = -1;
        int postId = -1;

        try {
            Part titlePart = req.getPart("title");
            if (titlePart != null) {
                title = new BufferedReader(new InputStreamReader(titlePart.getInputStream())).readLine();
            }

            Part contentPart = req.getPart("content");
            if (contentPart != null) {
                content = new BufferedReader(new InputStreamReader(contentPart.getInputStream())).readLine();
            }

            Part userIdPart = req.getPart("userId");
            if (userIdPart != null) {
                String userIdStr = new BufferedReader(new InputStreamReader(userIdPart.getInputStream())).readLine();
                userId = Integer.parseInt(userIdStr);
            }

            Part subredditIdPart = req.getPart("subredditId");
            if (subredditIdPart != null) {
                String subredditIdStr = new BufferedReader(new InputStreamReader(subredditIdPart.getInputStream())).readLine();
                subredditId = Integer.parseInt(subredditIdStr);
            }

            if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty() || userId == -1 || subredditId == -1) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing required post data (title, content, userId, subredditId).\"}");
                return;
            }

            try (Connection conn = DButil.getConnection()) {
                String sql = "INSERT INTO posts (user_id, subreddit_id, title, content, created_at) VALUES (?, ?, ?, ?, NOW())";
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, userId);
                stmt.setInt(2, subredditId);
                stmt.setString(3, title);
                stmt.setString(4, content);
                
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        postId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating post failed, no ID obtained.");
                    }
                } else {
                    throw new SQLException("Failed to insert post into database.");
                }
            } catch (SQLException e) {
                System.err.println("Database error during post insert: " + e.getMessage());
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error during post creation: " + e.getMessage());
                out.print(errorResponse.toString());
                return;
            }

            Collection<Part> parts = req.getParts();
            int mediaCount = 0;
            
            for (Part part : parts) {
                if (part.getName().equals("mediaFile") && part.getSize() > 0 && part.getSubmittedFileName() != null) {
                    String mediaUrl = null;
                    String mediaType = null;
                    String contentType = part.getContentType();
                    
                    if (contentType != null) {
                        if (contentType.startsWith("image/")) {
                            mediaType = "image";
                        } else if (contentType.startsWith("video/")) {
                            mediaType = "video";
                        } else {
                            mediaType = "raw";
                        }
                    } else {
                        mediaType = "raw";
                    }

                    InputStream inputStream = part.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[4096];
                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    byte[] fileBytes = buffer.toByteArray();

                    Map uploadResult;
                    Map options = ObjectUtils.asMap("resource_type", mediaType);
                    uploadResult = cloudinary.uploader().upload(fileBytes, options);
                    mediaUrl = (String) uploadResult.get("secure_url");

                    System.out.println("Uploaded media to Cloudinary: " + mediaUrl + " for Post ID: " + postId);

                    if (mediaUrl != null) {
                        try (Connection conn = DButil.getConnection()) {
                            String mediaSql = "INSERT INTO post_media (post_id, media_url, media_type, created_at) VALUES (?, ?, ?, NOW())";
                            PreparedStatement mediaStmt = conn.prepareStatement(mediaSql);
                            mediaStmt.setInt(1, postId);
                            mediaStmt.setString(2, mediaUrl);
                            mediaStmt.setString(3, mediaType);
                            mediaStmt.executeUpdate();
                            mediaCount++;
                        } catch (SQLException e) {
                            System.err.println("Database error inserting media URL: " + e.getMessage());
                        }
                    }
                }
            }

            JSONObject response = new JSONObject();
            response.put("message", "Post created successfully");
            response.put("postId", postId);
            response.put("mediaUploaded", mediaCount);
            out.print(response.toString());

        } catch (ServletException e) {
            System.err.println("Servlet error during multipart processing: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Error processing file upload: " + e.getMessage());
            out.print(errorResponse.toString());
        } catch (Exception e) {
            System.err.println("General error: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An unexpected error occurred: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
       // res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();

        String postIdParam = req.getParameter("id");
        String subredditIdParam = req.getParameter("subreddit_id");
       System.out.println("subredditIdParam: " + subredditIdParam);
        try (Connection conn = DButil.getConnection()) {
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT p.id AS postId, p.user_id, p.subreddit_id, p.title, p.content, p.created_at AS postCreatedAt, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = 1 THEN 1 ELSE 0 END), 0) AS upvote_count, ");
            sqlBuilder.append("COALESCE(SUM(CASE WHEN v.vote_type = -1 THEN 1 ELSE 0 END), 0) AS downvote_count, ");
            sqlBuilder.append("sm.id AS mediaId, sm.media_url, sm.media_type, sm.created_at AS mediaCreatedAt ");
            sqlBuilder.append("FROM posts p ");
            sqlBuilder.append("LEFT JOIN post_media sm ON p.id = sm.post_id ");
            sqlBuilder.append("LEFT JOIN votes v ON p.id = v.target_id AND v.target_type = 'post' ");
            
            // Handle different query parameters
            if (postIdParam != null && !postIdParam.trim().isEmpty()) {
                // Get specific post by ID
                sqlBuilder.append("WHERE p.id = ? ");
            } else if (subredditIdParam != null && !subredditIdParam.trim().isEmpty()) {
                // Get posts by subreddit ID
                sqlBuilder.append("WHERE p.subreddit_id = ? ");
            }
            // If no parameters, get all posts
            
            sqlBuilder.append("GROUP BY p.id, p.user_id, p.subreddit_id, p.title, p.content, p.created_at, sm.id, sm.media_url, sm.media_type, sm.created_at ");
            sqlBuilder.append("ORDER BY p.created_at DESC, sm.id ASC");

            PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString());
            
            // Set parameters based on what was provided
            if (postIdParam != null && !postIdParam.trim().isEmpty()) {
                stmt.setInt(1, Integer.parseInt(postIdParam));
            } else if (subredditIdParam != null && !subredditIdParam.trim().isEmpty()) {
                stmt.setInt(1, Integer.parseInt(subredditIdParam));
            }
            
            ResultSet rs = stmt.executeQuery();

            Map<Integer, JSONObject> postsMap = new HashMap<>();
            List<Integer> postOrder = new ArrayList<>();

            while (rs.next()) {
                int currentPostId = rs.getInt("postId");
                JSONObject postJson = postsMap.get(currentPostId);
                
                if (postJson == null) {
                    postJson = new JSONObject();
                    postJson.put("id", currentPostId);
                    postJson.put("user_id", rs.getInt("user_id"));
                    postJson.put("subreddit_id", rs.getInt("subreddit_id"));
                    postJson.put("title", rs.getString("title"));
                    postJson.put("content", rs.getString("content"));
                    postJson.put("created_at", rs.getTimestamp("postCreatedAt").toString());
                    postJson.put("upvote_count", rs.getInt("upvote_count"));
                    postJson.put("downvote_count", rs.getInt("downvote_count"));
                    postJson.put("media", new JSONArray());
                    
                    postsMap.put(currentPostId, postJson);
                    postOrder.add(currentPostId);
                }

                int mediaId = rs.getInt("mediaId");
                if (!rs.wasNull()) {
                    JSONObject mediaJson = new JSONObject();
                    mediaJson.put("id", mediaId);
                    mediaJson.put("media_url", rs.getString("media_url"));
                    mediaJson.put("media_type", rs.getString("media_type"));
                    mediaJson.put("created_at", rs.getTimestamp("mediaCreatedAt").toString());
                    postJson.getJSONArray("media").put(mediaJson);
                }
            }

            JSONArray postsArray = new JSONArray();
            for (Integer postIdFromOrder : postOrder) {
                postsArray.put(postsMap.get(postIdFromOrder));
            }

            // Handle response based on query type
            if (postIdParam != null && !postIdParam.trim().isEmpty()) {
                // Single post request
                if (postsArray.length() > 0) {
                    out.print(postsArray.get(0).toString());
                } else {
                    res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("error", "Post not found");
                    out.print(errorResponse.toString());
                }
            } else {
                // Multiple posts request (all posts or posts by subreddit)
                out.print(postsArray.toString());
            }

        } catch (NumberFormatException e) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid ID format");
            out.print(errorResponse.toString());
        } catch (SQLException e) {
            System.err.println("Database error in doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Database error: " + e.getMessage());
            out.print(errorResponse.toString());
        } catch (Exception e) {
            System.err.println("General error in doGet: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "An unexpected error occurred: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }
}
