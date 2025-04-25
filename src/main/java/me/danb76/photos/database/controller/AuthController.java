package me.danb76.photos.database.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import me.danb76.photos.database.repositories.AttemptsRepository;
import me.danb76.photos.database.tables.LoginAttempts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.core.userdetails.UserDetails; // Import UserDetails
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping(path = "/api/auth/")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AttemptsRepository attemptsRepository;
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    public AuthController(AuthenticationManager authenticationManager, AttemptsRepository attemptsRepository) {
        this.authenticationManager = authenticationManager;
        this.attemptsRepository = attemptsRepository;
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ipAddress = getIpAddress(request);
        String userAgent = getUserAgent(request);

        long failedAttempts = attemptsRepository.countFailedAttempts(ipAddress, 60 * 60 * 24 * 1000); // Within 24 hours

        if (failedAttempts >= 5) {
            logger.warn("Blocking login for user '{}' from IP {} due to {} recent failed attempts.",
                    username, ipAddress, failedAttempts);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Too many failed login attempts. Please try again later.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }

        try {
            UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);
            Authentication authentication = authenticationManager.authenticate(authRequest);
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
            logger.debug("Login: Session ID after authentication: {}", session.getId());

            logger.info("Successful login for user '{}' from IP {} with User Agent {}", username, ipAddress, userAgent);
            LoginAttempts attempt = new LoginAttempts();
            attempt.setSuccess(true);
            attempt.setIp_addr(ipAddress);
            attempt.setUsername(username);
            attempt.setUser_agent(userAgent);
            attemptsRepository.save(attempt);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Login successful");
            responseBody.put("username", username);
            responseBody.put("roles", authentication.getAuthorities());
            return ResponseEntity.ok(responseBody);

        } catch (AuthenticationException e) {
            logger.warn("Failed login attempt for user '{}' from IP address: {} (User-Agent: {})", username, ipAddress, userAgent);
            LoginAttempts attempt = new LoginAttempts();
            attempt.setSuccess(false);
            attempt.setIp_addr(ipAddress);
            attempt.setUsername(username);
            attempt.setUser_agent(userAgent);
            attemptsRepository.save(attempt);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
    }

    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = "unknown";
        if(authentication != null && authentication.getPrincipal() != null){
            Object principal = authentication.getPrincipal();
            username = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername() : principal.toString();
        }


        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            logger.info("User '{}' logged out", username);
        }

        SecurityContextHolder.clearContext();

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Logged out successfully!");
        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/protected")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> protectedResource(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        String sessionId = (session != null) ? session.getId() : "No session";
        logger.debug("Protected: Session ID for request: {}", sessionId);

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            logger.debug("Protected: Authentication principal name: {}", username);
            logger.debug("Protected: Authentication authorities: {}", authentication.getAuthorities());

            /*
                        if (username.equals("anonymousUser")) {
                logger.warn("Protected: User is authenticated but principal is anonymousUser.");
                return unauthorised(username);
            }
             */

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Access granted");
            responseBody.put("username", username);
            responseBody.put("authorities", authentication.getAuthorities());
            return ResponseEntity.ok(responseBody);
        } else {
            logger.warn("Protected: No authenticated principal found.");
            return unauthorised(null);
        }
    }

    private ResponseEntity<Map<String, Object>> unauthorised(String username) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("message", "Unauthorized");
        errorResponse.put("username", username);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    private String getIpAddress(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("X-Forwarded-For")).orElse(request.getRemoteAddr());
    }

    private String getUserAgent(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
    }
}