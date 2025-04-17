package me.danb76.photos.security;

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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Configuration
    @Profile("development")
    public static class DevelopmentSecurityConfig {
        @Bean
        @ConditionalOnMissingBean(UserDetailsService.class)
        InMemoryUserDetailsManager inMemoryUserDetailsManager() {
            String generatedPassword = "{noop}admin";
            return new InMemoryUserDetailsManager(
                    User.withUsername("user").password(generatedPassword).roles("ADMIN").build()
            );
        }

        @Bean
        @ConditionalOnMissingBean(AuthenticationEventPublisher.class)
        DefaultAuthenticationEventPublisher defaultAuthenticationEventPublisher(ApplicationEventPublisher delegate) {
            return new DefaultAuthenticationEventPublisher(delegate);
        }

        @Bean
        SecurityFilterChain configure(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth ->
                            auth
                                    .requestMatchers("/categories/all").permitAll()
                                    .requestMatchers("/categories/**").hasRole("ADMIN")
                                    .requestMatchers("/upload/**").hasRole("ADMIN")
                                    .requestMatchers("/jobs/**").hasRole("ADMIN")
                                    .requestMatchers("/photos/category/**").permitAll()
                    )
                    .httpBasic(withDefaults());
            return http.build();
        }
    }
}