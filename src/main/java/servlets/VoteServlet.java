package servlets;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.DButil;
public class VoteServlet extends HttpServlet {
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
        int userId = -1;
        int targetId = -1;
        String targetType = null;
        int voteType = 0; // 1: up, -1: down
        try {
            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            userId = jsonObject.get("userId").getAsInt();
            targetId = jsonObject.get("targetId").getAsInt();
            targetType = jsonObject.get("targetType").getAsString();
            voteType = jsonObject.get("voteType").getAsInt();
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
                        String deleteSql = "DELETE FROM votes WHERE id = ?";
                        PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                        deleteStmt.setInt(1, existingVoteId);
                        deleteStmt.executeUpdate();
                        conn.commit(); 
                        out.print("{\"message\":\"Vote removed successfully\", \"action\":\"removed\"}");
                    } else if (voteType == 0) { 
                        String deleteSql = "DELETE FROM votes WHERE id = ?";
                        PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                        deleteStmt.setInt(1, existingVoteId);
                        deleteStmt.executeUpdate();
                        conn.commit();
                        out.print("{\"message\":\"Vote removed successfully\", \"action\":\"removed\"}");
                    } else {
                        String updateSql = "UPDATE votes SET vote_type = ? WHERE id = ?";
                        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                        updateStmt.setInt(1, voteType);
                        updateStmt.setInt(2, existingVoteId);
                        updateStmt.executeUpdate();
                        conn.commit();
                        out.print("{\"message\":\"Vote updated successfully\", \"action\":\"updated\"}");
                    }
                } else {
                    if (voteType != 0) {
                        String insertSql = "INSERT INTO votes (user_id, target_id, target_type, vote_type, created_at) VALUES (?, ?, ?, ?, NOW())";
                        PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
                        insertStmt.setInt(1, userId);
                        insertStmt.setInt(2, targetId);
                        insertStmt.setString(3, targetType);
                        insertStmt.setInt(4, voteType);
                        insertStmt.executeUpdate();
                        conn.commit();
                        out.print("{\"message\":\"Vote cast successfully\", \"action\":\"cast\"}");
                    } else {
                        conn.rollback();
                        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        out.print("{\"error\":\"No existing vote to remove\"}");
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
                out.print("{\"error\":\"Database error: " + e.getMessage() + "\"}");
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
            out.print("{\"error\":\"Invalid JSON format or missing fields: " + e.getMessage() + "\"}");
        }

    }}
