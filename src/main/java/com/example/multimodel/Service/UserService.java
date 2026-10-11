package com.example.multimodel.Service;

import com.example.multimodel.Config.SecurityConfig;
import com.example.multimodel.DB.UserRepo;
import com.example.multimodel.Encoder.PasswordEncode;
import com.example.multimodel.Entities.User;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


@Service
public class UserService {

    @Autowired
    UserRepo userRepo;

    @Autowired
    PasswordEncode passwordEncode;

    public User register(User user) throws ResponseStatusException
    {
        if(userRepo.findByUsername(user.getUsername()) != null)
        {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"User already exists");
        }

        user.setPassword(passwordEncode.passwordEncodeFun().encode(user.getPassword()));

        try
        {
            return userRepo.save(user);
        }
        catch (Exception e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }
    }

    public User getUserByUsername(String username)
    {
        try {
            return userRepo.findByUsername(username);
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }

    }

}
