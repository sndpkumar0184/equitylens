package com.equitylens.ai;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/** Local-only by default. A configured bearer token is required on every research request. */
@Configuration
public class ResearchAccessFilter {
    @Bean
    FilterRegistrationBean<Filter> researchAccess(@Value("${equitylens.ai.access-token:}")String token) {
        Filter filter=new Filter(){@Override public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)throws IOException,ServletException {
            var request=(HttpServletRequest)req;var response=(HttpServletResponse)res;
            boolean authorized=token.isBlank()?Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr()):
                    MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),String.valueOf(request.getHeader("Authorization")).getBytes(StandardCharsets.UTF_8));
            if(!authorized){response.sendError(403,"Research access denied");return;}
            chain.doFilter(req,res);
        }};
        var registration=new FilterRegistrationBean<>(filter);registration.addUrlPatterns("/mcp","/api/assistant/*");
        registration.setAsyncSupported(true);registration.setOrder(-100);return registration;
    }
}
