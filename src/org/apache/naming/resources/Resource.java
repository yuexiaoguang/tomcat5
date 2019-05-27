package org.apache.naming.resources;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Encapsultes 资源的内容
 */
public class Resource {
    
    // ----------------------------------------------------------- Constructors
    
    public Resource() {
    }
    
    
    public Resource(InputStream inputStream) {
        setContent(inputStream);
    }
    
    
    public Resource(byte[] binaryContent) {
        setContent(binaryContent);
    }
    
    
    // ----------------------------------------------------- Instance Variables
    
    
    /**
     * 二进制内容
     */
    protected byte[] binaryContent = null;
    
    
    /**
     * Input stream.
     */
    protected InputStream inputStream = null;
    
    
    // ------------------------------------------------------------- Properties
    
    
    public InputStream streamContent()
        throws IOException {
        if (binaryContent != null) {
            return new ByteArrayInputStream(binaryContent);
        }
        return inputStream;
    }
    
    
    public byte[] getContent() {
        return binaryContent;
    }
    
    
    public void setContent(InputStream inputStream) {
        this.inputStream = inputStream;
    }
    
    
    public void setContent(byte[] binaryContent) {
        this.binaryContent = binaryContent;
    }
}
