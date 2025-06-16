package listeners;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
public class CleanupListener implements ServletContextListener {
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            AbandonedConnectionCleanupThread.checkedShutdown(); // Shuts down that thread
        } catch (Exception e) {
            System.err.println("Error shutting down MySQL Cleanup Thread: " + e.getMessage());
        }
    }
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        
    }
}
