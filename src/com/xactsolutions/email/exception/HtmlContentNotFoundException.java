package com.xactsolutions.email.exception;

public class HtmlContentNotFoundException extends RuntimeException {

    public HtmlContentNotFoundException() {
        super("There is no html content found in this email!!");
    }

}
