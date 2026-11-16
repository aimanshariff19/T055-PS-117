package com.ps117.infrastructure;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {
    public List<String[]> read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try (CSVParser parser = CSVParser.parse(Files.newInputStream(path), StandardCharsets.UTF_8, CSVFormat.DEFAULT)) {
            List<String[]> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                String[] values = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    values[i] = record.get(i);
                }
                rows.add(values);
            }
            return rows;
        }
    }
}
