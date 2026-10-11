package com.example.multimodel.Controller;

import ai.onnxruntime.OrtException;
import com.example.multimodel.DB.ResultRepo;
import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import com.example.multimodel.Records.ReportRequest;
import com.example.multimodel.Records.ResultResponse;
import com.example.multimodel.Service.ReportService;
import com.example.multimodel.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<ResultResponse> pdf_upload(String username, @RequestParam("file") MultipartFile file)
    {

        if (file.isEmpty() || !"application/pdf".equals(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"file cant be read");

        User user = userService.getUserByUsername(username);
        Result r = reportService.GenerateResultViaPdf(user,file);
        return new ResponseEntity<>(new ResultResponse(
                r.getId(), r.getCath(), r.getCath_prob(), r.getLAD(), r.getLAD_prob(),
                r.getLCX(), r.getLCX_prob(), r.getRCA(), r.getRCA_prob()) ,HttpStatus.OK);
    }

    @PostMapping("/Report")
    public ResponseEntity<ResultResponse> getResult(Authentication auth, @Valid @RequestBody ReportRequest report)
    {
        Report rep = reportService.createReport(userService.getUserByUsername(auth.getName()), report);
        try {
            Result r = reportService.GenerateResult(rep);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ResultResponse(
                    r.getId(), r.getCath(), r.getCath_prob(), r.getLAD(), r.getLAD_prob(),
                    r.getLCX(), r.getLCX_prob(), r.getRCA(), r.getRCA_prob()));
        } catch (OrtException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }

    }
}
