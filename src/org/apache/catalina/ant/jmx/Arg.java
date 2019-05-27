package org.apache.catalina.ant.jmx;

public class Arg {
    String type;
    String value;

    public void setType( String type) {
        this.type=type;
    }
    public void setValue( String value ) {
        this.value=value;
    }
    public void addText( String text ) {
        this.value=text;
    }

    public String getValue() {
        return value;
    }

    public String getType() {
        return type;
    }
}
