package org.example;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelFlightParser {

    public List<FlightRecord> parse(MultipartFile file) throws Exception {
        List<FlightRecord> records = new ArrayList<>();
        try (InputStream is = file.getInputStream()) {
            Workbook wb = WorkbookFactory.create(is);
            Sheet sh = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            for (int i = 1; i <= sh.getLastRowNum(); i++) {
                Row row = sh.getRow(i);
                if (row == null) continue;

                String center = fmt.formatCellValue(row.getCell(0));
                String shr = fmt.formatCellValue(row.getCell(1));
                String dep = fmt.formatCellValue(row.getCell(2));
                String arr = fmt.formatCellValue(row.getCell(3));

                if (shr.isBlank() && dep.isBlank() && arr.isBlank()) continue;

                FlightRecord rec = ShrDepArrParser.parse(center, shr, dep, arr);
                records.add(rec);
            }
        }
        return records;
    }
}
