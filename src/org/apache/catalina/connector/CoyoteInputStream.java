package org.apache.catalina.connector;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.servlet.ServletInputStream;

import org.apache.catalina.security.SecurityUtil;

/**
 * 这个类处理读字节.
 */
public class CoyoteInputStream extends ServletInputStream {

    // ----------------------------------------------------- Instance Variables

    protected InputBuffer ib;

    // ----------------------------------------------------------- Constructors

    protected CoyoteInputStream(InputBuffer ib) {
        this.ib = ib;
    }

    // -------------------------------------------------------- Package Methods

    /**
     * 清空.
     */
    void clear() {
        ib = null;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 防止克隆.
     */
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    // --------------------------------------------- ServletInputStream Methods

    public int read() throws IOException {    
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = new Integer(ib.readByte());
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.readByte();
        }
    }

    public int available() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){
                            public Object run() throws IOException{
                                Integer integer = new Integer(ib.available());
                                return integer;
                            }
                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
           return ib.available();
        }    
    }

    public int read(final byte[] b) throws IOException {
        
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = 
                                    new Integer(ib.read(b, 0, b.length));
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.read(b, 0, b.length);
         }        
    }


    public int read(final byte[] b, final int off, final int len) throws IOException {
            
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = 
                                    new Integer(ib.read(b, off, len));
                                return integer;
                            }
                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.read(b, off, len);
        }            
    }


    public int readLine(byte[] b, int off, int len) throws IOException {
        return super.readLine(b, off, len);
    }


    /** 
     * 关闭流
     * 自从重新循环, 不允许调用 super.close().
     */
    public void close() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(
                    new PrivilegedExceptionAction(){

                        public Object run() throws IOException{
                            ib.close();
                            return null;
                        }
                });
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
             ib.close();
        }            
    }
}
