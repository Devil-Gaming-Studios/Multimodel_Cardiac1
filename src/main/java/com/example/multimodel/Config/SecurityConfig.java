package com.example.multimodel.Config;

import com.example.multimodel.Encoder.PasswordEncode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    PasswordEncode passwordEncode;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return passwordEncode.passwordEncodeFun();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(request -> request
                        // public: the UI files and the two open endpoints
                        .requestMatchers("/", "/index.html", "/assets/**", "/models/**",
                                "/favicon.ico", "/health", "/login", "/register", "/error","/setup/status").permitAll()
                        .anyRequest().authenticated());

        http.formLogin(c -> c.disable());

        // Basic auth, but answer 401 WITHOUT the "WWW-Authenticate: Basic" header,
        // otherwise the browser pops up its own ugly login dialog on a wrong password.
        http.httpBasic(basic -> basic.authenticationEntryPoint(
                (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")));

        return http.build();
    }
}