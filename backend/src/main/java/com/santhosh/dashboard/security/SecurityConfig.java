package com.santhosh.dashboard.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Set this on Render to your Vercel frontend URL.
     * Example:
     * https://your-frontend.vercel.app
     */
    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * BCrypt password encoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS configuration for frontend and local development.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                frontendUrl,
                "http://localhost:5173",
                "http://localhost:3000"
        ));

        config.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));

        config.setExposedHeaders(List.of("Set-Cookie"));

        // Required for JSESSIONID/session authentication
        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    /**
     * Main Spring Security configuration.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http

            // CORS
            .cors(cors ->
                    cors.configurationSource(corsConfigurationSource())
            )

            // Authorization
            .authorizeHttpRequests(auth -> auth

                // Static resources
                .requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/fonts/**"
                ).permitAll()

                // CORS preflight
                .requestMatchers(
                        org.springframework.http.HttpMethod.OPTIONS,
                        "/**"
                ).permitAll()

                // Public endpoints
                .requestMatchers(
                        "/login",
                        "/register",
                        "/api/users/register",
                        "/api/agent/telemetry",
                        "/access-denied",
                        "/error",
                        "/actuator/health"
                ).permitAll()

                // Admin endpoints
                .requestMatchers("/admin/**")
                .hasAnyRole("SUPER_ADMIN", "ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Form Login
            .formLogin(form -> form

                .loginPage("/login")

                .loginProcessingUrl("/login")

                .usernameParameter("username")

                .passwordParameter("password")

                .successHandler(successHandler())

                .failureHandler(failureHandler())

                .permitAll()
            )

            // Remember Me
            .rememberMe(remember -> remember

                .key("sentinelcore-remember-me-key")

                .tokenValiditySeconds(
                        7 * 24 * 60 * 60
                )

                .userDetailsService(userDetailsService)
            )

            // Logout
            .logout(logout -> logout

                .logoutRequestMatcher(
                        new AntPathRequestMatcher(
                                "/logout",
                                "POST"
                        )
                )

                .logoutSuccessHandler(
                        (request, response, authentication) -> {

                            response.setStatus(
                                    HttpServletResponse.SC_OK
                            );

                            response.setContentType(
                                    "application/json"
                            );

                            response.getWriter().write(
                                    "{\"success\": true}"
                            );
                        }
                )

                .invalidateHttpSession(true)

                .deleteCookies(
                        "JSESSIONID",
                        "remember-me"
                )

                .permitAll()
            )

            // Session Management
            .sessionManagement(session -> session

                .maximumSessions(1)

                .expiredUrl("/login?expired=true")
            )

            // CSRF
            .csrf(csrf -> csrf

                .csrfTokenRepository(
                        CookieCsrfTokenRepository
                                .withHttpOnlyFalse()
                )

                .csrfTokenRequestHandler(
                        new CsrfTokenRequestAttributeHandler()
                )

                /*
                 * REST APIs and SPA authentication endpoints
                 * are excluded from CSRF validation.
                 */
                .ignoringRequestMatchers(
                        "/api/**",
                        "/login",
                        "/logout",
                        "/register"
                )
            )

            // Exception Handling
            .exceptionHandling(ex -> ex

                // Not authenticated
                .authenticationEntryPoint(
                        (request, response, authException) -> {

                            response.setStatus(
                                    HttpServletResponse.SC_UNAUTHORIZED
                            );

                            response.setContentType(
                                    "application/json"
                            );

                            response.getWriter().write(
                                    "{\"error\": \"401 Unauthorized — please log in\"}"
                            );
                        }
                )

                // Insufficient permissions
                .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {

                            response.setStatus(
                                    HttpServletResponse.SC_FORBIDDEN
                            );

                            response.setContentType(
                                    "application/json"
                            );

                            response.getWriter().write(
                                    "{\"error\": \"403 Access Denied — insufficient permissions\"}"
                            );
                        }
                )
            );

        return http.build();
    }

    /**
     * Login success handler.
     */
    private AuthenticationSuccessHandler successHandler() {

        return (request, response, authentication) -> {

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.setContentType(
                    "application/json"
            );

            response.getWriter().write(
                    "{\"success\": true}"
            );
        };
    }

    /**
     * Login failure handler.
     *
     * TEMPORARY DIAGNOSTIC LOGGING:
     * This prints the authentication exception to Render logs
     * so we can determine why the admin login is failing.
     */
    private AuthenticationFailureHandler failureHandler() {

        return (request, response, exception) -> {

            System.err.println(
                    "========== LOGIN FAILURE =========="
            );

            System.err.println(
                    "Username: "
                    + request.getParameter("username")
            );

            System.err.println(
                    "Exception: "
                    + exception.getClass().getName()
            );

            System.err.println(
                    "Message: "
                    + exception.getMessage()
            );

            exception.printStackTrace();

            System.err.println(
                    "==================================="
            );

            String errorParam;

            if (exception instanceof LockedException) {

                errorParam = "locked";

            } else if (
                    exception.getMessage() != null
                    && exception.getMessage()
                            .toLowerCase()
                            .contains("disabled")
            ) {

                errorParam = "disabled";

            } else {

                errorParam = "true";
            }

            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
            );

            response.setContentType(
                    "application/json"
            );

            response.getWriter().write(
                    "{\"error\": \"" + errorParam + "\"}"
            );
        };
    }
}