package com.example.multimodel.Config;

import com.example.multimodel.Encoder.PasswordEncode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.net.PasswordAuthentication;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    PasswordEncode passwordEncode;

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return passwordEncode.passwordEncodeFun();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
    {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(request -> request.
                        requestMatchers("/health","/login","/register").permitAll().
                        anyRequest().authenticated())
                ;
        http.formLogin(c -> c.disable())
                .logout(Customizer.withDefaults());
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }

}
