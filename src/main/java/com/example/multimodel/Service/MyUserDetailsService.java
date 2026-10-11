package com.example.multimodel.Service;

import com.example.multimodel.DB.UserRepo;
import com.example.multimodel.Entities.User;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;


    @Override
    public UserDetails loadUserByUsername(String username)
    {
        User u = userRepo.findByUsername(username);
        return org.springframework.security.core.userdetails.
                User.withUsername(u.getUsername())
                .password(u.getPassword())
                .build();
    }
}
