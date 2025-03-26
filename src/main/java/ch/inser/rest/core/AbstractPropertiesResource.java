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

package ch.inser.rest.core;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Abstract Service to access the properties of the application needed by the front-end.
 *
 * Implémented with Inser token or OIDC
 *
 */
public abstract class AbstractPropertiesResource {

    /**
     * The property key name to find the properties to expose to the front-end.
     */
    protected static final String FRONTEND_PROPERTIES = "frontend.properties";

    /**
     * The property key name to find the properties to expose to the front-end.
     */
    protected static final String FRONTEND_PROPERTIES_FREE = "frontend.properties.free";

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;
}
