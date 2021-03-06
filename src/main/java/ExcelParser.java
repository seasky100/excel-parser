import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author cznno
 * Date: 2019/3/7
 */
public class ExcelParser {

    private Map<String, Sheet> sheetMap;

    private String[] sharedStrings;

    private ZipFile zipFile;

    public ExcelParser() {
    }

    public void load(InputStream stream) throws IOException, XMLStreamException {

        File zip = File.createTempFile("excel-zip", "zip");
        Files.copy(stream, zip.toPath(), REPLACE_EXISTING);
        this.zipFile = new ZipFile(zip);

        this.sheetMap = WorkBookInfo.parseSheet(zipFile);
        this.sharedStrings = WorkBookInfo.parseSharedStrings(zipFile);
    }

    public List<List<Cell>> readSheet(String sheetName, int limit, int offset) throws IOException, XMLStreamException {
        String sheetId = sheetMap.get(sheetName).getSheetId();
        ZipEntry entry = this.zipFile.getEntry("xl/worksheets/sheet" + sheetId + ".xml");
        XMLEventReader xmlEventReader = XMLInputFactory.newInstance()
                                                       .createXMLEventReader(this.zipFile.getInputStream(entry));

        boolean cellValueFound = false;
        boolean cellIsString = false;
        boolean start = false;

        int count = 0;

        List<List<Cell>> rows = new LinkedList<>();
        List<Cell> column = new ArrayList<>();

        while (xmlEventReader.hasNext()) {
            XMLEvent event = xmlEventReader.nextEvent();
            if (event.isStartElement()) {
                StartElement se = event.asStartElement();
                if (se.getName().getLocalPart().equalsIgnoreCase("row")) {
                    if (offset >= 0) {
                        offset--;
                        continue;
                    } else {
                        start = true;
                    }
                    rows.add(column);
                    column = new LinkedList<>();
                    count++;
                } else if (se.getName().getLocalPart().equalsIgnoreCase("v")) {
                    cellValueFound = true;
                } else if (se.getName().getLocalPart().equalsIgnoreCase("c")) {
                    cellIsString = se.getAttributeByName(new QName("t")) != null;
                }
            } else if (event.isCharacters() && cellValueFound && start) {
                column.add(new Cell(event.asCharacters().getData(), cellIsString));
            }

            if (count > limit) {
                break;
            }
        }

        for (final List<Cell> cells : rows) {
            for (final Cell cell : cells) {
                if (cell.isString())
                    cell.setValue(sharedStrings[(Integer.parseInt(cell.getValue()))]);
            }
        }

        return rows;
    }
}
