package me.danb76.photos.security;

import me.danb76.photos.service.JobsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${web.url}")
    private String webUrl;

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

}