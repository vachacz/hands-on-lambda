package pl.vachacz.transcoder;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class XmlTranscoder {

    private CSVPrinter printer;
    private XmlVisitor xmlVisitor;
    private XmlProcessor xmlProcessor;

    public XmlTranscoder(XmlProcessor xmlProcessor, OutputStream outputStream) {
        this.xmlProcessor = xmlProcessor;
        try {
            this.printer = new CSVPrinter(new OutputStreamWriter(outputStream), CSVFormat.RFC4180.withHeader("latitude", "longitude", "id", "name"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void transcode() {
        xmlProcessor.transcode(this::handleXmlEvent);
    }

    private void handleXmlEvent() {
        xmlProcessor.visitNode("POI", this::placeStarted, this::visitPlace, this::placeCompleted);
    }

    private void placeStarted() {
        this.xmlVisitor = new XmlVisitor();
    }

    private void visitPlace() {
        xmlProcessor.visitNode("Identity", this::visitIdentity);
        xmlProcessor.visitNode("Location", this::visitLocation);
    }

    private void visitIdentity() {
        xmlProcessor.visitNode("POI_Name", this::visitName);
        xmlProcessor.visitLeaf("POI_Entity_ID", xmlVisitor::visitId);
    }

    private void visitName() {
        xmlProcessor.visitLeaf("Text", xmlVisitor::visitGermanName);
    }

    private void visitLocation() {
        xmlProcessor.visitLeaf("Latitude", xmlVisitor::visitDisplayLatitude);
        xmlProcessor.visitLeaf("Longitude", xmlVisitor::visitDisplayLongitude);
    }

    private void placeCompleted() {
        try {
            this.printer.printRecord(xmlVisitor.getCsvRecord());
            this.printer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class XmlVisitor {

        private BigDecimal latitude;
        private BigDecimal longitude;
        private Long id;
        private String name;

        void visitDisplayLatitude(String latitude) {
            this.latitude = new BigDecimal(latitude);
        }
        void visitDisplayLongitude(String longitude) {
            this.longitude = new BigDecimal(longitude);
        }
        void visitId(String id) {
            this.id = Long.valueOf(id);
        }
        void visitGermanName(String name) {
            this.name = name;
        }

        List<Object> getCsvRecord() {
            return Arrays.asList(latitude, longitude, id, name);
        }

    }

}
