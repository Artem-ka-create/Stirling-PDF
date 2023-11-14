package stirling.software.SPDF;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import stirling.software.SPDF.controller.api.CropController;
import stirling.software.SPDF.controller.api.MergeController;
import stirling.software.SPDF.controller.api.converters.ConvertEpubToPdf;
import stirling.software.SPDF.controller.api.converters.ConvertMarkdownToPdf;
import stirling.software.SPDF.model.api.GeneralFile;
import stirling.software.SPDF.model.api.general.CropPdfForm;
import stirling.software.SPDF.model.api.general.MergePdfsRequest;
import stirling.software.SPDF.utils.FileToPdf;
import stirling.software.SPDF.utils.WebResponseUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.bouncycastle.asn1.cms.CMSAttributes.contentType;
import static org.junit.Assert.*;
import static stirling.software.SPDF.utils.FileToPdf.convertHtmlToPdf;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = WavefrontProperties.Application.class)

public class SpringTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Autowired
    private MockMvc mvc;




    @Test
    public void NotRightFileFormatToEpubPdfController() throws Exception {

        ConvertEpubToPdf convertEpubToPdf = new ConvertEpubToPdf();
        GeneralFile genFile = new GeneralFile();

        genFile.setFileInput(getTestMultiPartPdf());

        String exceptionmsg = "File must be in .epub format.";

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> convertEpubToPdf.epubToSinglePdf(genFile));
        assertEquals(exceptionmsg, exception.getMessage());

    }

    @Test
    public void EmptyRequestToEpubPdfController() throws Exception {

        ConvertEpubToPdf convertEpubToPdf = new ConvertEpubToPdf();
        GeneralFile genFile = new GeneralFile();

        String exceptionmsg = "Please provide an EPUB file for conversion.";

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> convertEpubToPdf.epubToSinglePdf(genFile));
        assertEquals(exceptionmsg, exception.getMessage());

    }

    @Test
    public void NotRightFileFormatToMarkDownController() throws Exception {

        ConvertMarkdownToPdf convertMd = new ConvertMarkdownToPdf();
        GeneralFile genFile = new GeneralFile();
        genFile.setFileInput(getTestMultiPartPdf());

        String exceptionmsg = "File must be in .md format.";

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> convertMd.markdownToPdf(genFile));
        assertEquals(exceptionmsg, exception.getMessage());

    }


    @Test
    public void EmptyRequestToMarkDownController() throws Exception {

        ConvertMarkdownToPdf convertMd = new ConvertMarkdownToPdf();
        GeneralFile genFile = new GeneralFile();
//        genFile.setFileInput(getTestMultiPartPdf());

        String exceptionmsg = "Please provide a Markdown file for conversion.";

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> convertMd.markdownToPdf(genFile));
        assertEquals(exceptionmsg, exception.getMessage());

    }



    @Test
    public void TwoSimilarPdfsMerge() throws IOException {

        MergeController mrgeController = new MergeController();
        MergePdfsRequest mergeRequest = new MergePdfsRequest();
        MultipartFile multiPartFile = getTestMultiPartPdf();
        MultipartFile[] multiPartArr = {multiPartFile, multiPartFile};
        List<PDDocument> documents = new ArrayList<>();
        for (MultipartFile file : multiPartArr) {
            try (InputStream is = file.getInputStream()) {
                documents.add(PDDocument.load(is));
            }
        }

        PDDocument mergedDoc = mrgeController.mergeDocuments(documents);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        mergedDoc.save(baos);

        byte[] pdfContent = baos.toByteArray();
        mergeRequest.setFileInput(multiPartArr);

        ResponseEntity<byte[]> response = WebResponseUtils.pdfDocToWebResponse(mergedDoc, multiPartArr[0].getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_merged.pdf");
        mergedDoc.close();

        assertEquals(Arrays.toString(pdfContent), Arrays.toString(Objects.requireNonNull(response.getBody())));
    }

    @Test
    public void EmptySortTypeToMergeController() throws IOException {

        MergeController mrgeController = new MergeController();
        MergePdfsRequest mergeRequest = new MergePdfsRequest();
        MultipartFile multiPartFile = getTestMultiPartPdf();
        MultipartFile[] multiPartArr = {multiPartFile, multiPartFile};
        mergeRequest.setFileInput(multiPartArr);

        ResponseEntity<byte[]> response = mrgeController.mergePdfs(mergeRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());

    }


    @Test
    public void EmptyFileToMergeController() throws IOException {

        MergeController mrgeController = new MergeController();
        MergePdfsRequest mergeRequest = new MergePdfsRequest();
        mergeRequest.setSortType("orderProvided");

        assertThrows(NullPointerException.class, () -> mrgeController.mergePdfs(mergeRequest));

    }


    @Test
    public void EmptyRequestToCropController() {

        CropController crp = new CropController();
        CropPdfForm crpForm = new CropPdfForm();
        assertThrows(NullPointerException.class, () -> crp.cropPdf(crpForm));
    }

    @Test
    public void SendCropControllerPDFFile() throws IOException {

        CropController crp = new CropController();
        CropPdfForm crpForm = new CropPdfForm();

        crpForm.setFileInput(getTestMultiPartPdf());

        ResponseEntity<byte[]> bte = crp.cropPdf(crpForm);
        Assert.assertEquals(HttpStatus.OK, bte.getStatusCode());
    }

    @Test()
    public void EmptyHtmlToPdfConvertTest() {

        byte[] emptyByteArr = new byte[0];
        String emptyByteArrFile = "";

        String exceptionmsg = "Cannot invoke \"java.nio.file.Path.getFileSystem()\" because \"path\" is null";

        Throwable exception = assertThrows(NullPointerException.class, () -> convertHtmlToPdf(emptyByteArr, emptyByteArrFile));
        assertEquals(exceptionmsg, exception.getMessage());
    }

    protected MultipartFile getTestMultiPartPdf() throws IOException {
        Path path = Paths.get("src/test/java/stirling/software/SPDF/playloads/readmeTestPdf.pdf");
        String name = "readmeTestPdf.pdf";
        String originalFileName = "readmeTestPdf.pdf";
        String contentType = "text/plain";
        byte[] content = null;

        content = Files.readAllBytes(path);

        MultipartFile result = new MockMultipartFile(name,
                originalFileName, contentType, content);
        return result;
    }

    protected MultipartFile getTestMultiPartMarkDown() throws IOException {
        Path path = Paths.get("src/test/java/stirling/software/SPDF/playloads/ReadmeTestMarkDown.md");
        String name = "ReadmeTestMarkDown.md";
        String originalFileName = "ReadmeTestMarkDown.md";
        String contentType = "text/plain";
        byte[] content = null;

        content = Files.readAllBytes(path);

        MultipartFile result = new MockMultipartFile(name,
                originalFileName, contentType, content);
        return result;
    }

}


