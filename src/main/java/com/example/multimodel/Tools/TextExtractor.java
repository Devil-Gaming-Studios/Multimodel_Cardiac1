package com.example.multimodel.Tools;

import com.example.multimodel.Records.PageText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class TextExtractor {
    public List<PageText> text_extractor(byte[] pdfBytes) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= document.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(new PageText(p, stripper.getText(document).trim(), false));
            }
        }
        return pages;
    }
}
