package com.example.multimodel.Service;

import com.example.multimodel.Records.PageText;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReportService {

    @Autowired
    PdfExtractionService pdfExtractionService;

    public Result GenerateResultViaPdf(User user, MultipartFile file)
    {
        try {
            List<PageText> pages = pdfExtractionService.extract(file.getBytes());
            Result result = new Result();

            result.setLAD((byte) pages.get(0).page());
            System.out.println(pages.getFirst().text());
            return result;
        }catch (Exception e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }
    }
}
