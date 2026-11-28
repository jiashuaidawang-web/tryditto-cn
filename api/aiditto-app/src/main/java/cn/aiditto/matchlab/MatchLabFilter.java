package cn.aiditto.matchlab;

import cn.aiditto.domain.common.ErrorCode;
import cn.aiditto.infra.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects the research API before authorization when the switch is off. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MatchLabFilter extends OncePerRequestFilter {
    private final MatchLabProperties properties;

    public MatchLabFilter(MatchLabProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/match-lab")) {
            if (!properties.admits()) {
                Problems.write(response, ErrorCode.FORBIDDEN, "Match Lab 在这个环境关闭");
                return;
            }
            long length = request.getContentLengthLong();
            if (length > cn.aiditto.domain.matchlab.LabWorkbench.MAX_BODY_BYTES) {
                Problems.write(response, ErrorCode.VALIDATION, "PAYLOAD");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
