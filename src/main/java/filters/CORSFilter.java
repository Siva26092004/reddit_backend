package filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CORSFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("Access-Control-Allow-Origin","http://localhost:4200");
        res.setHeader("Access-Control-Allow-Methods","GET,POST,PUT,DELETE,OPTIONS");
        res.setHeader("Access-Control-Allow-Headers","Origin, Content-Type,Accept,Authorization");
        res.setHeader("Access-Control-Allow-Credentials","true");
        chain.doFilter(request, response);
    }
    @Override
    public void destroy() {}
}
