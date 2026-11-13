package cn.aiditto.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** {@code Authorization: Bearer <token>} → {@link AuthUser}. A bad token is simply anonymous; the entry point answers 401. */
public class BearerTokenFilter extends OncePerRequestFilter {
    private final SessionService sessions;

    public BearerTokenFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            sessions.resolve(header.substring(7).strip()).ifPresent(user -> {
                List<SimpleGrantedAuthority> roles = user.admin()
                        ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var auth = UsernamePasswordAuthenticationToken.authenticated(user, null, roles);
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }
}
