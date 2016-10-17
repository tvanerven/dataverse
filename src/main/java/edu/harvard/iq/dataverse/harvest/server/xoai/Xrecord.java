
package edu.harvard.iq.dataverse.harvest.server.xoai;

import com.lyncode.xoai.model.oaipmh.Header;
import com.lyncode.xoai.model.oaipmh.Record;
import com.lyncode.xoai.xml.XmlWriter;
import static com.lyncode.xoai.xml.XmlWriter.defaultContext;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.export.ExportException;
import edu.harvard.iq.dataverse.export.ExportService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Leonid Andreev
 * 
 * This is the Dataverse extension of XOAI Record, 
 * optimized to directly output a pre-exported metadata record to the 
 * output stream, thus by-passing expensive parsing and writing by 
 * an XML writer, as in the original XOAI implementation.
 */

public class Xrecord extends Record {
    private static String METADATA_FIELD = "metadata";
    private static String METADATA_START_ELEMENT = "<"+METADATA_FIELD+">";
    private static String METADATA_END_ELEMENT = "</"+METADATA_FIELD+">";
    private static String HEADER_FIELD = "header";
    private static String STATUS_ATTRIBUTE = "status";
    private static String IDENTIFIER_FIELD = "identifier";
    private static String DATESTAMP_FIELD = "datestamp";
    private static String SETSPEC_FIELD = "setSpec";
    
    protected Dataset dataset; 
    protected String formatName;
    
    
    public Dataset getDataset() {
        return dataset; 
    }
    
    public Xrecord withDataset(Dataset dataset) {
        this.dataset = dataset;
        return this;
    }
    
    
    public String getFormatName() {
        return formatName; 
    }
    
    
    public Xrecord withFormatName(String formatName) {
        this.formatName = formatName;
        return this;
    }
    
    public void writeToStream(OutputStream outputStream) throws IOException {
        outputStream.flush();

        String headerString = itemHeaderToString(this.header); 
        
        if (headerString == null) {
            throw new IOException("Xrecord: failed to stream item header.");
        }
        
        outputStream.write(headerString.getBytes());
        outputStream.write(METADATA_START_ELEMENT.getBytes());
                
        outputStream.flush();

        if (dataset != null && formatName != null) {
            InputStream inputStream = null;
            try {
                inputStream = ExportService.getInstance().getExport(dataset, formatName);
            } catch (ExportException ex) {
                inputStream = null;
            }

            if (inputStream == null) {
                throw new IOException("Xrecord: failed to open metadata stream.");
            }
            writeMetadataStream(inputStream, outputStream);
        }
        outputStream.write(METADATA_END_ELEMENT.getBytes());
        outputStream.flush();

    }
    
    private String itemHeaderToString(Header header) {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            XmlWriter writer = new XmlWriter(byteOutputStream, defaultContext());

            writer.writeStartElement(HEADER_FIELD);

             if (header.getStatus() != null) {
                writer.writeAttribute(STATUS_ATTRIBUTE, header.getStatus().value());
             }
            writer.writeElement(IDENTIFIER_FIELD, header.getIdentifier());
            writer.writeElement(DATESTAMP_FIELD, header.getDatestamp());
            for (String setSpec : header.getSetSpecs()) {
                writer.writeElement(SETSPEC_FIELD, setSpec);
            }
            writer.writeEndElement(); // header
            writer.flush();
            writer.close();

            String ret = byteOutputStream.toString();

            return ret;
        } catch (Exception ex) {
            return null;
        }
    }
    
    private void writeMetadataStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        int bufsize;
        byte[] buffer = new byte[4 * 8192];

        while ((bufsize = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bufsize);
            outputStream.flush();
        }

        inputStream.close();
    }
    
}
