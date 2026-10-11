package com.example.multimodel.Controller;

import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import com.example.multimodel.Service.ReportService;
import com.example.multimodel.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


@RestController
public class Controller {
    @Autowired
    UserService userService;

    @Autowired
    ReportService reportService;

    @GetMapping("/health")
    public ResponseEntity<String> health()
    {
        return new ResponseEntity<>("server is working", HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user)
    {
        User u = userService.register(user);
        return new ResponseEntity<>(u.getUsername(), HttpStatus.OK);
    }

    @PostMapping("/PDF")
    public ResponseEntity<Result> pdf_upload(String username,@RequestParam("file") MultipartFile file)
    {

        if (file.isEmpty() || !"application/pdf".equals(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"file cant be read");

        User user = userService.getUserByUsername(username);

        return new ResponseEntity<>( reportService.GenerateResultViaPdf(user,file),HttpStatus.OK);
    }

    @PostMapping("/Report")
    public ResponseEntity<Result> pdf_upload(String username, @RequestBody Report report)
    {

    }
}
