package com.example.multimodel.Tools;

import com.example.multimodel.Records.PageText;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class OCR_Tool {
    public List<PageText> ocr_tool(byte[] pdfBytes, List<Integer> pageNumbers) throws IOException, TesseractException {
        Tesseract tesseract = new Tesseract();          // created once, not per page
        tesseract.setDatapath(".\\tessdata");
        tesseract.setLanguage("eng");

        List<PageText> out = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int p : pageNumbers) {
                BufferedImage image = renderer.renderImageWithDPI(p - 1, 300);
                out.add(new PageText(p, tesseract.doOCR(image).trim(), true));
            }
        }
        return out;
    }
}
