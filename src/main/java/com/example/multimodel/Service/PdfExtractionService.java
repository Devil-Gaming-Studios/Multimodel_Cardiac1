package com.example.multimodel.Service;

import com.example.multimodel.Records.PageText;
import com.example.multimodel.Tools.OCR_Tool;
import com.example.multimodel.Tools.TextExtractor;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PdfExtractionService {

    @Autowired
    TextExtractor textExtractor;

    @Autowired
    OCR_Tool ocrTool;

    public List<PageText> extract(byte[] pdfBytes) throws IOException, TesseractException {
        List<PageText> pages = textExtractor.text_extractor(pdfBytes);

        List<Integer> empty = pages.stream().filter(pg -> pg.text().length() < 30).map(PageText::page).toList();
        if (!empty.isEmpty()) {
            Map<Integer, PageText> ocr = ocrTool.ocr_tool(pdfBytes, empty).stream()
                    .collect(Collectors.toMap(PageText::page, x -> x));
            pages = pages.stream().map(pg -> ocr.getOrDefault(pg.page(), pg)).toList();
        }
        return pages;   // save each PageText as a row/chunk; ocr=true means "low confidence" warning
    }
}
