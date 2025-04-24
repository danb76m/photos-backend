package me.danb76.photos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.danb76.photos.database.repositories.AttemptsRepository;
import me.danb76.photos.database.tables.LoginAttempts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(2)
public class LoginInterceptorFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoginInterceptorFilter.class);

    @Autowired
    private AttemptsRepository attemptsRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String ipAddress = Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(request.getRemoteAddr());
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            String username = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername() : principal.toString();

            long failedAttempts = attemptsRepository.countFailedAttempts(ipAddress, 60 * 60 * 24 * 1000); // Consider a shorter timeframe

            if (failedAttempts >= 5) {
                logger.warn("Blocking login for user '{}' from IP {} due to {} recent failed attempts.",
                        username, ipAddress, failedAttempts);
                response.sendError(429, "Too many failed login attempts. Please try again later.");
                return;
            } else {
                logger.info("Successful login for user '{}' from IP {}", username, ipAddress);
                LoginAttempts attempt = new LoginAttempts();
                attempt.setSuccess(true);
                attempt.setIp_addr(ipAddress);
                attempt.setUsername(username);
                attempt.setUser_agent(userAgent);
                attemptsRepository.save(attempt);
            }
        }

        filterChain.doFilter(request, response); // Continue the chain
    }
}