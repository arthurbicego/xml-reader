package br.com.nanodata.xmlreader.services;

import br.com.nanodata.xmlreader.exceptions.NotFoundException;
import br.com.nanodata.xmlreader.models.dtos.FileDataDTO;
import br.com.nanodata.xmlreader.models.dtos.SaveDTO;
import br.com.nanodata.xmlreader.models.entities.FileContent;
import br.com.nanodata.xmlreader.models.entities.FileData;
import br.com.nanodata.xmlreader.models.mappers.FileDataMapper;
import br.com.nanodata.xmlreader.repositories.FileDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ReaderService {

    @Autowired
    private FileDataRepository fileDataRepository;

    @Autowired
    private FileDataMapper fileDataMapper;

    public SaveDTO processFiles(List<MultipartFile> files) {
        List<String> fileNameList = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                this.processEachFile(file);
                fileNameList.add((file.getOriginalFilename()));
            } catch (Exception e) {
                log.error("Um erro ocorreu ao processar o seguinte arquivo: " + file.getName(), e);
            }
        }
        return new SaveDTO(fileNameList.size() + " arquivo(s) salvo(s) com sucesso: " + fileNameList);
    }

    private void processEachFile(MultipartFile file) throws IOException, ParserConfigurationException, SAXException,
            XPathExpressionException {
        FileContent fileContent = convertIntoFileContent(file);
        FileData fileData = convertIntoFileData(file, fileContent);
        fileDataRepository.save(fileData);
    }

    private FileContent convertIntoFileContent(MultipartFile file) throws IOException {
        FileContent fileContent = new FileContent();
        fileContent.setContent(file.getBytes());
        fileContent.setOriginalFileName(file.getOriginalFilename());
        return fileContent;
    }

    private FileData convertIntoFileData(MultipartFile file, FileContent fileContent) throws IOException, SAXException,
            ParserConfigurationException, XPathExpressionException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputStream inputStream = new ByteArrayInputStream(file.getBytes());
        Document document = builder.parse(inputStream);

        FileData fileData = new FileData();

        fileData.setFileId(getTagValue(document, "/nfeProc/NFe/infNFe/@Id"));
        fileData.setIdedhEmi(LocalDateTime.parse(getTagValue(document, "/nfeProc/NFe/infNFe/ide/dhEmi"), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        fileData.setIdecUF(getTagValue(document, "/nfeProc/NFe/infNFe/ide/cUF"));
        fileData.setIdenNF(getTagValue(document, "/nfeProc/NFe/infNFe/ide/nNF"));
        fileData.setEmitxFant(getTagValue(document, "/nfeProc/NFe/infNFe/emit/xFant"));
        fileData.setEmitCNPJ(getTagValue(document, "/nfeProc/NFe/infNFe/emit/CNPJ"));
        fileData.setDestCNPJ(getTagValue(document, "/nfeProc/NFe/infNFe/dest/CNPJ"));
        fileData.setDestxNome(getTagValue(document, "/nfeProc/NFe/infNFe/dest/xNome"));
        fileData.setIcmstotvTotTrib(Double.parseDouble(getTagValue(document, "/nfeProc/NFe/infNFe/total/ICMSTot/vTotTrib")));
        fileData.setIcmstotvNF(Double.parseDouble(getTagValue(document, "/nfeProc/NFe/infNFe/total/ICMSTot/vNF")));

        fileData.setFileContent(fileContent);

        return fileData;
    }

    private String getTagValue(Document document, String fileTag) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        return xPath.compile(fileTag).evaluate(document, XPathConstants.STRING).toString();
    }

    public List<FileDataDTO> getAll() {
        List<FileData> listFileData = fileDataRepository.findAll();
        return listFileData.stream().map(fileDataMapper::toDTO).toList();
    }

    public ResponseEntity<byte[]> downloadFileById(Long fileDataId) throws NotFoundException {
        FileData fileData = findFileDataById(fileDataId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename(fileData.getFileContent().getOriginalFileName()).build());
        return new ResponseEntity<>(fileData.getFileContent().getContent(), headers, HttpStatus.OK);
    }

    private FileData findFileDataById(Long id) throws NotFoundException {
        Optional<FileData> optionalFileData = fileDataRepository.findById(id);
        if (optionalFileData.isPresent()) {
            return optionalFileData.get();
        } else {
            NotFoundException exception = new NotFoundException("Nenhum arquivo encontrado com o ID: " + id);
            log.error("Um erro ocorreu ao processar o arquivo com ID: " + id, exception);
            throw exception;
        }
    }
}
