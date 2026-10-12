package com.example.multimodel.Service;

import ai.onnxruntime.OrtException;
import com.example.multimodel.DB.ReportRepo;
import com.example.multimodel.DB.ResultRepo;
import com.example.multimodel.Entities.Report;
import com.example.multimodel.Records.PageText;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import com.example.multimodel.Records.ReportRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportService {

    @Autowired
    PdfExtractionService pdfExtractionService;

    @Autowired
    ReportRepo reportRepo;

    @Autowired
    ModelService modelService;

    @Autowired
    ResultRepo resultRepo;

    @Autowired
    Validator validator;

    @Autowired
    ReportFieldExtractor reportFieldExtractor;

    public Result GenerateResultViaPdf(User user, MultipartFile file) {
        try {
            List<PageText> pages = pdfExtractionService.extract(file.getBytes());
            String text = pdfExtractionService.toTaggedText(pages);

            ReportRequest req = reportFieldExtractor.extract(text);   // the new piece

            Set<ConstraintViolation<ReportRequest>> bad = validator.validate(req);
            System.out.println("VALIDATION: " + bad);
            if (!bad.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Could not read these fields from the PDF: " + bad.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage()).toList());
            }

            Report rep = createReport(user, req);

            System.out.println("EXTRACTED: " + req);
            return GenerateResult(rep);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("PDF processing failed"+ e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to process PDF: " + e.getMessage(), e);
        }
    }

    public Report createReport(User user, ReportRequest r)
    {
        Report rep = new Report();

        rep.setUser(user);
        rep.setAge(r.age().byteValue());
        rep.setWeight(r.weight().shortValue());
        rep.setLength(r.length().shortValue());
        rep.setSex(r.sex().byteValue());
        rep.setBMI(r.BMI());
        rep.setDM(r.DM().byteValue());
        rep.setHTN(r.HTN().byteValue());
        rep.setCurrent_smoker(r.current_smoker().byteValue());
        rep.setEx_smoker(r.ex_smoker().byteValue());
        rep.setFH(r.FH().byteValue());
        rep.setObesity(r.Obesity().byteValue());
        rep.setCRF(r.CRF().byteValue());
        rep.setCVA(r.CVA().byteValue());
        rep.setAirway_disease(r.Airway_disease().byteValue());
        rep.setThyroid_disease(r.Thyroid_disease().byteValue());
        rep.setCHF(r.CHF().byteValue());
        rep.setDLP(r.DLP().byteValue());
        rep.setBP(r.BP().shortValue());
        rep.setPR(r.PR().shortValue());
        rep.setEdema(r.Edema().byteValue());
        rep.setWeak_Peripheral_Pulse(r.Weak_Peripheral_Pulse().byteValue());
        rep.setLung_rales(r.Lung_rales().byteValue());
        rep.setSystolic_Murmur(r.Systolic_Murmur().byteValue());
        rep.setDiastolic_Murmur(r.Diastolic_Murmur().byteValue());
        rep.setTypical_Chest_Pain(r.Typical_Chest_Pain().byteValue());
        rep.setDyspnea(r.Dyspnea().byteValue());
        rep.setFunction_class(r.Function_class().byteValue());
        rep.setAtypical(r.Atypical().byteValue());
        rep.setNonanginal(r.Nonanginal().byteValue());
        rep.setExertional_CP(r.Exertional_CP().byteValue());
        rep.setLowTH_Ang(r.LowTH_Ang().byteValue());
        rep.setQ_Wave(r.Q_Wave().byteValue());
        rep.setSt_Elevation(r.St_Elevation().byteValue());
        rep.setSt_Depression(r.St_Depression().byteValue());
        rep.setTinversion(r.Tinversion().byteValue());
        rep.setLVH(r.LVH().byteValue());
        rep.setBBB(r.BBB().byteValue());
        rep.setFBS(r.FBS().shortValue());
        rep.setCR(r.CR());
        rep.setTG(r.TG().shortValue());
        rep.setLDL(r.LDL().shortValue());
        rep.setHDL(r.HDL());
        rep.setBUN(r.BUN().shortValue());
        rep.setESR(r.ESR().shortValue());
        rep.setHB(r.HB());
        rep.setK(r.K());
        rep.setNa(r.Na().shortValue());
        rep.setWBC(r.WBC());
        rep.setLymph(r.Lymph().byteValue());
        rep.setNeut(r.Neut().byteValue());
        rep.setPLT(r.PLT().shortValue());
        rep.setEF_TTE(r.EF_TTE().byteValue());
        rep.setRegion_RWMA(r.Region_RWMA().byteValue());
        rep.setVHD(r.VHD().byteValue());
        rep.setUser(user);

        saveReport(rep);

        return rep;
    }

    public void saveReport(Report report)
    {
        try {
            reportRepo.save(report);
        }
        catch (Exception e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.toString());
        }
    }

    public Result GenerateResult(Report report) throws OrtException {
        Map<String, ModelService.TargetOutput> out = modelService.ModelOutput(report);

        Result result = new Result();
        result.setLAD((byte) (out.get("LAD").predictedPositive() ? 1 : 0));
        result.setLCX((byte) (out.get("LCX").predictedPositive() ? 1 : 0));
        result.setRCA((byte) (out.get("RCA").predictedPositive() ? 1 : 0));
        result.setCath((byte) (out.get("CAD").predictedPositive() ? 1 : 0));

        result.setLAD_prob(out.get("LAD").probability());
        result.setLCX_prob(out.get("LCX").probability());
        result.setRCA_prob(out.get("RCA").probability());
        result.setCath_prob(out.get("CAD").probability());

        result.setReport(report);
        resultRepo.save(result);
        return result;
    }

    public ReportRequest extractFromPdf(MultipartFile file) {
        try {
            List<PageText> pages = pdfExtractionService.extract(file.getBytes());
            String text = pdfExtractionService.toTaggedText(pages);
            return reportFieldExtractor.extract(text);   // no validation here: the UI lets the user fill gaps
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read PDF");
        }
    }
}
