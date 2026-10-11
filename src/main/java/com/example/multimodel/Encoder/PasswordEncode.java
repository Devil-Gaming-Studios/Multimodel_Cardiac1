package com.example.multimodel.Encoder;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncode {
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    public PasswordEncoder passwordEncodeFun()
    {

        return passwordEncoder;
    }
}
