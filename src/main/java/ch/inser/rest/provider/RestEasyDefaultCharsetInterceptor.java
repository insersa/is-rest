/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package ch.inser.rest.provider;

import java.io.IOException;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

/**
 * If charset not given, default to UTF-8 and not us-ascii (MIME RFC) or ISO-8859-1
 *
 * http://stackoverflow.com/questions/14683677/how-to-set-encoding-in-resteasy-to-utf-8
 */
@Provider
public class RestEasyDefaultCharsetInterceptor implements ReaderInterceptor {
    /**
     * Using string value instead of constant to limit references to RestEasy // (this should be possible to set through web.xml imo) //
     * private static final String RESTEASY_DEFAULT_CHARSET_PROPERTY = //
     * org.jboss.resteasy.plugins.providers.multipart.InputPart.DEFAULT_CHARSET_PROPERTY;
     */
    private static final String RESTEASY_DEFAULT_CHARSET_PROPERTY = "resteasy.provider.multipart.inputpart.defaultCharset";

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext ctx) throws IOException, WebApplicationException {
        ctx.setProperty(RESTEASY_DEFAULT_CHARSET_PROPERTY, "UTF-8");
        return ctx.proceed();
    }
}
