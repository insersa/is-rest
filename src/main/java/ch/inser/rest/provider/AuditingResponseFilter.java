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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.util.RestUtil;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

/**
 * Auditing REST request.
 *
 * To use it declare the filter in the application
 *
 * <servlet>
 *
 * <servlet-name>RestEasy REST Service</servlet-name>
 *
 * ...
 *
 * <init-param>
 *
 * <param-name>resteasy.providers</param-name>
 *
 * <param-value>...,ch.inser.rest.provider.AuditingResponseFilter,...</param-value>
 *
 * <init-param>
 *
 * ..
 *
 * </servlet>
 *
 *
 * and set the logger in the logging config.
 */
public class AuditingResponseFilter implements ContainerResponseFilter {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(AuditingResponseFilter.class);

    @Override
    public void filter(ContainerRequestContext aRequestContext, ContainerResponseContext aResponseContext) throws IOException {
        try {
            if (aRequestContext.getHeaderString("Token") != null) {
                RestUtil.getClaims(aRequestContext.getHeaderString("Token"));
            }
            logger.info(aResponseContext.getStatus() + ":" + aResponseContext.getMediaType());
        } catch (ISSecurityException e) {
            logger.error(aResponseContext.getStatus() + ":" + aResponseContext.getMediaType() + " ***Security error", e);
        } finally {
            RestUtil.cleanNdc();
        }
    }

}
