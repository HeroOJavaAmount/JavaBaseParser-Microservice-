package ru.netology.serialize;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import ru.netology.dto.Block;
import ru.netology.dto.Document;
import ru.netology.exception.SerializeException;

import java.io.IOException;
import java.io.StringWriter;

/**
 * @params
 * @metods
 * @return
 */
public class CsvDocumentSerializer implements DocumentSerializer {
    @Override
    public String serialize(Document document) {
        try (StringWriter sw = new StringWriter();
             CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT
                     .withHeader("type", "text", "headingLevel"))) {
            for (Block block : document.blocks()) {
                printer.printRecord(block.type(), block.text(), block.headingLevel());
            }
            return sw.toString();
        } catch (IOException ex) {
            throw new SerializeException("Can not serialize to CSV", ex);
        }
    }
    @Override public String contentType() { return "text/csv"; }
    @Override public String fileExtension() { return "csv"; }
}