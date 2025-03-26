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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resteasy.spi.ApplicationException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Global exception mapper that intercepts all exceptions thrown by the application code and responds with a code 500 without stack trace
 *
 * Security precaution. Users should not receive information about the application from stack traces.
 *
 * @author INSER SA *
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<ApplicationException> {

    /** */
    private static final Log logger = LogFactory.getLog(GlobalExceptionMapper.class);

    /**
     * Catches all exceptions thrown from application code
     *
     * @param aException
     *            application code exception, ex. nullpointerexception
     * @return error response without stack trace
     */
    @Override
    public Response toResponse(ApplicationException aException) {
        logger.error("Error in a REST service", aException);
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity("An error occured").type(MediaType.TEXT_PLAIN).build();
    }
}
