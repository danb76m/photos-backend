package me.danb76.photos.security.listeners;

import jakarta.servlet.http.HttpServletRequest;
import me.danb76.photos.database.repositories.AttemptsRepository;
import me.danb76.photos.database.tables.LoginAttempts;
import me.danb76.photos.security.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class FailAuthenticateListener {
    @Autowired
    private AttemptsRepository attemptsRepository;

    private static final Logger logger = LoggerFactory.getLogger(FailAuthenticateListener.class);

    @EventListener
    public void authenticateFail(AuthenticationFailureBadCredentialsEvent e) {
        Object principal = e.getAuthentication().getPrincipal();
        String username = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername() : principal.toString();
        if (username == null || username.isEmpty()) {
            username = "unknown";
        }

        Optional<HttpServletRequest> requestOptional = getCurrentHttpRequest();
        String finalUsername = username;
        requestOptional.ifPresent(request -> {
            String ipAddress = Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(request.getRemoteAddr());
            String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
            logger.warn("Failed login attempt for user '{}' from IP address: {} (User-Agent: {})", finalUsername, ipAddress, userAgent);

            LoginAttempts attempt = new LoginAttempts();
            attempt.setSuccess(false);
            attempt.setIp_addr(ipAddress);
            attempt.setUsername(finalUsername);
            attempt.setUser_agent(userAgent);
            attemptsRepository.save(attempt);
        });

        if (requestOptional.isEmpty()) {
            logger.warn("Failed login attempt for user '{}' (IP and User-Agent not available)", username);
        }
    }

    private Optional<HttpServletRequest> getCurrentHttpRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

}
