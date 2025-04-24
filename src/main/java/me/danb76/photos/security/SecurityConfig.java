package me.danb76.photos.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.danb76.photos.database.repositories.AttemptsRepository;
import me.danb76.photos.database.tables.LoginAttempts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private AttemptsRepository attemptsRepository;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${web.url}")
    private String webUrl;

    @Value("${security.maxFailedAttempts:5}")
    private long maxFailedAttempts;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(inMemoryUserDetailsManager(passwordEncoder()))
                .passwordEncoder(passwordEncoder());
        return auth.getOrBuild();
    }

    @Bean
    public CustomAuthenticationFilter customAuthenticationFilter(AuthenticationManager authenticationManager) {
        return new CustomAuthenticationFilter(authenticationManager, attemptsRepository, maxFailedAttempts);
    }

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

    @EventListener
    public void authenticateSuccess(AuthenticationSuccessEvent e) {
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
            logger.info("Successful login for user '{}' from IP address: {} (User-Agent: {})", finalUsername, ipAddress, userAgent);

            LoginAttempts attempt = new LoginAttempts();
            attempt.setSuccess(true);
            attempt.setIp_addr(ipAddress);
            attempt.setUsername(finalUsername);
            attempt.setUser_agent(userAgent);
            attemptsRepository.save(attempt);
        });

        if (requestOptional.isEmpty()) {
            logger.info("Successful login for user '{}' (IP and User-Agent not available)", username);
        }
    }

    private Optional<HttpServletRequest> getCurrentHttpRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    InMemoryUserDetailsManager inMemoryUserDetailsManager(PasswordEncoder passwordEncoder) {
        String encodedPassword = passwordEncoder.encode(adminPassword);
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername).password(encodedPassword).roles("ADMIN").build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
        private final AttemptsRepository attemptsRepository;
        private final AuthenticationManager authenticationManager;
        private final long maxAttempts;

        public CustomAuthenticationFilter(AuthenticationManager authenticationManager, AttemptsRepository attemptsRepository, long maxAttempts) {
            this.attemptsRepository = attemptsRepository;
            this.authenticationManager = authenticationManager;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
            String ipAddress = Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(request.getRemoteAddr());
            long currentTime = System.currentTimeMillis();

            long failedAttempts = attemptsRepository.countFailedAttempts(ipAddress, currentTime - 60 * 60 * 24 * 1000);
            if (failedAttempts >= maxAttempts) {
                response.setStatus(429);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\": \"Too many failed login attempts. Please try again later.\"}");
                } catch (IOException e) {
                    logger.error("Error writing rate-limit response", e);
                }
                return null;
            }

            String username = obtainUsername(request);
            String password = obtainPassword(request);
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);
            return authenticationManager.authenticate(authRequest);
        }
    }
}
