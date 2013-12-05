package com.vaynberg.wicket.select2;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Application;
import org.apache.wicket.Request;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.util.string.Strings;

public class CharsetExtractor {
    private CharsetExtractor() {}
    
    public static Charset extractCharset(Request request) {
	Charset charset = null;
	if (request instanceof WebRequest) {
	    WebRequest webRequest = (WebRequest) request;
	    HttpServletRequest servletRequest = webRequest.getHttpServletRequest();
	    String charsetName = servletRequest.getCharacterEncoding();
	    if (charsetName != null) {
		try {
		    charset = Charset.forName(charsetName);
		} catch (UnsupportedCharsetException ex) {}
	    }
	}
	if (charset == null) {
	    charset = getDefaultCharset();
	}
	return charset;
    }

    private static Charset getDefaultCharset() {
	String charsetName = null;

	if (Application.exists()) {
	    charsetName = Application.get().getRequestCycleSettings().getResponseRequestEncoding();
	}
	if (Strings.isEmpty(charsetName)) {
	    charsetName = "UTF-8";
	}
	return Charset.forName(charsetName);
    }
}
