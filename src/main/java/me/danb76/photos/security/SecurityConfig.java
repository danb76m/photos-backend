package me.danb76.photos.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.danb76.photos.database.repositories.AttemptsRepository;
import me.danb76.photos.database.tables.LoginAttempts;
import me.danb76.photos.service.JobsService;
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
import org.springframework.security.web.authentication.WebAuthenticationDetails;
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

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${web.url}")
    private String webUrl;

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
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");
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
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            String userAgent = request.getHeader("User-Agent");
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

    // Development

    @Configuration
    @Profile("development")
    public class DevelopmentSecurityConfig {
        @Bean
        @ConditionalOnMissingBean(AuthenticationEventPublisher.class)
        DefaultAuthenticationEventPublisher defaultAuthenticationEventPublisher(ApplicationEventPublisher delegate) {
            return new DefaultAuthenticationEventPublisher(delegate);
        }

        @Bean
        SecurityFilterChain configure(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(auth ->
                            auth
                                    .requestMatchers("/api/categories/all").permitAll()
                                    .requestMatchers("/api/categories/**").hasRole("ADMIN")
                                    .requestMatchers("/api/upload/**").hasRole("ADMIN")
                                    .requestMatchers("/api/jobs/**").hasRole("ADMIN")
                                    .requestMatchers("/api/photos/category/**").permitAll()
                                    .requestMatchers("/api/photos/**").permitAll()
                                    .requestMatchers("/api/photos/delete/**").hasRole("ADMIN")
                                    .requestMatchers("/api/actuator/**").hasRole("ADMIN")
                                    .anyRequest().authenticated()
                    )
                    .addFilterBefore(new UsernamePasswordAuthenticationFilter(authenticationManager), UsernamePasswordAuthenticationFilter.class)
                    .httpBasic(withDefaults());
            return http.build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            logger.info("WEB URL === {}", webUrl);
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(Arrays.asList(webUrl));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
            configuration.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }
    }

    // Production

    @Configuration
    @Profile("production")
    public class ProductionSecurityConfig {
        @Bean
        @ConditionalOnMissingBean(AuthenticationEventPublisher.class)
        DefaultAuthenticationEventPublisher defaultAuthenticationEventPublisher(ApplicationEventPublisher delegate) {
            return new DefaultAuthenticationEventPublisher(delegate);
        }

        @Bean
        SecurityFilterChain configure(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(auth ->
                            auth
                                    .requestMatchers("/api/categories/all").permitAll()
                                    .requestMatchers("/api/categories/**").hasRole("ADMIN")
                                    .requestMatchers("/api/upload/**").hasRole("ADMIN")
                                    .requestMatchers("/api/jobs/**").hasRole("ADMIN")
                                    .requestMatchers("/api/photos/category/**").permitAll()
                                    .requestMatchers("/api/photos/**").permitAll()
                                    .requestMatchers("/api/photos/delete/**").hasRole("ADMIN")
                                    .requestMatchers("/api/actuator/**").hasRole("ADMIN")
                                    .anyRequest().authenticated()
                    )
                    .httpBasic(withDefaults());
            return http.build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            logger.info("WEB URL === {}", webUrl);
            logger.info("We're in production now!");
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(Arrays.asList(webUrl));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
            configuration.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }
    }

    private class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

        private final AuthenticationManager authenticationManager;

        public CustomAuthenticationFilter(AuthenticationManager authenticationManager) {
            this.authenticationManager = authenticationManager;
        }

        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
            String ipAddress = request.getHeader("X-Forwarded-For");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            long currentTime = System.currentTimeMillis();

            // Check the number of failed attempts within the last 24 hours for this IP address
            long failedAttempts = attemptsRepository.countFailedAttempts(ipAddress, currentTime - 60 * 60 * 24 * 1000);
            if (failedAttempts >= 5) {
                response.setStatus(429); // too many requests
                try {
                    response.getWriter().write("Too many failed login attempts. Please try again later.");
                } catch (IOException e) {
                    return null; // TODO better.
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