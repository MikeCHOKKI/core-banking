package com.corebanking.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
public class MdcFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
            MDC.put("method", ((HttpServletRequest) request).getMethod());
            MDC.put("path", ((HttpServletRequest) request).getRequestURI());
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
