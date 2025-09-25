package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

@RestController
public class UploadController {
    private final ExcelFlightParser parser;
    private List<FlightRecord> cachedRecords;

    public UploadController(ExcelFlightParser parser) {
        this.parser = parser;
    }

    @PostMapping("/upload")
    public List<FlightRecord> upload(@RequestParam("file") MultipartFile file) throws Exception {
        cachedRecords = parser.parse(file);
        return cachedRecords;
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        if (cachedRecords == null || cachedRecords.isEmpty()) {
            throw new RuntimeException("Нет данных для выгрузки. Сначала загрузите Excel через /upload");
        }

        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=flights.json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(response.getOutputStream(), cachedRecords);
    }
}