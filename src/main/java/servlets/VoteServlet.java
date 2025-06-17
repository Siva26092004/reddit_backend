package servlets;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import org.json.JSONObject;
import utils.DButil;
public class VoteServlet extends HttpServlet {
  /*   @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type");
        res.setStatus(HttpServletResponse.SC_OK);
    }*/
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
  //      res.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        res.setContentType("application/json");
        PrintWriter out = res.getWriter();
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        String body = sb.toString();
        int userId = -1;
        int targetId = -1;
        String targetType = null;
        int voteType = 0; // 1: up, -1: down
        try {
            JSONObject jsonObject = new JSONObject(body);
            userId = jsonObject.getInt("userId");
            targetId = jsonObject.getInt("targetId");
            targetType = jsonObject.getString("targetType");
            voteType = jsonObject.getInt("voteType");
            if (userId == -1 || targetId == -1 || targetType == null || targetType.trim().isEmpty() || (voteType != 1 && voteType != -1 && voteType != 0)) {
                res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing or invalid vote data (userId, targetId, targetType, voteType).\"}");
                return;
            }
            Connection conn = null;
            try {
                conn = DButil.getConnection();
                conn.setAutoCommit(false);

                String selectSql = "SELECT id, vote_type FROM votes WHERE user_id = ? AND target_id = ? AND target_type = ?";
                PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                selectStmt.setInt(1, userId);
                selectStmt.setInt(2, targetId);
                selectStmt.setString(3, targetType);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    int existingVoteId = rs.getInt("id");
                    int existingVoteType = rs.getInt("vote_type");
                    if (existingVoteType == voteType) {
                        // Same vote type - remove the vote
                        String deleteSql = "DELETE FROM votes WHERE id = ?";
                        PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                        deleteStmt.setInt(1, existingVoteId);
                        deleteStmt.executeUpdate();
                        conn.commit();        
                        JSONObject response = new JSONObject();
                        response.put("message", "Vote removed successfully");
                        response.put("action", "removed");
                        out.print(response.toString());
                    } else if (voteType == 0) {
                        // Remove vote (voteType 0 means remove)
                        String deleteSql = "DELETE FROM votes WHERE id = ?";
                        PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                        deleteStmt.setInt(1, existingVoteId);
                        deleteStmt.executeUpdate();
                        conn.commit();
                        
                        JSONObject response = new JSONObject();
                        response.put("message", "Vote removed successfully");
                        response.put("action", "removed");
                        out.print(response.toString());
                    } else {
                        // Different vote type - update the vote
                        String updateSql = "UPDATE votes SET vote_type = ? WHERE id = ?";
                        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                        updateStmt.setInt(1, voteType);
                        updateStmt.setInt(2, existingVoteId);
                        updateStmt.executeUpdate();
                        conn.commit();
                        
                        JSONObject response = new JSONObject();
                        response.put("message", "Vote updated successfully");
                        response.put("action", "updated");
                        out.print(response.toString());
                    }
                } else {
                    // No existing vote
                    if (voteType != 0) {
                        // Insert new vote
                        String insertSql = "INSERT INTO votes (user_id, target_id, target_type, vote_type, created_at) VALUES (?, ?, ?, ?, NOW())";
                        PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                        insertStmt.setInt(1, userId);
                        insertStmt.setInt(2, targetId);
                        insertStmt.setString(3, targetType);
                        insertStmt.setInt(4, voteType);
                        insertStmt.executeUpdate();
                        conn.commit();
                        
                        JSONObject response = new JSONObject();
                        response.put("message", "Vote cast successfully");
                        response.put("action", "cast");
                        out.print(response.toString());
                    } else {
                        // Trying to remove a vote that doesn't exist
                        conn.rollback();
                        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("error", "No existing vote to remove");
                        out.print(errorResponse.toString());
                    }
                }

            } catch (SQLException e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.err.println("Error during rollback: " + ex.getMessage());
                    }
                }
                System.err.println("Database error during vote operation: " + e.getMessage());
                res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error: " + e.getMessage());
                out.print(errorResponse.toString());
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing connection: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing JSON or processing vote: " + e.getMessage());
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Invalid JSON format or missing fields: " + e.getMessage());
            out.print(errorResponse.toString());
        }
    }
}
