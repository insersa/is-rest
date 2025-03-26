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

package ch.inser.rest.services.object;

import io.swagger.annotations.Api;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

/**
 * Variante publique de objects resource
 *
 * @author INSER SA *
 */
@Path("/public")
@Api(value = "public")
public class PublicResource {

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Lien avec la ressource qui permet d'accéder aux objets métier par objectname
     *
     * Defines that the next path parameter after public is treated as a parameter and passed to the PublicNamesResource. Allows to type
     * http://url/projet/services/public/{objectname}. {objectname} will be treated as parameter object and passed to PublicNamesResource
     *
     * @param aObjectName
     *            nom de l'objet métier
     * @return ressource pour accéder aux objets métiers par nom
     */
    @Path("{objectname}")
    public PublicNamesResource getObjectNamesResource(@PathParam("objectname") String aObjectName) {
        return new PublicNamesResource(iContext, aObjectName);
    }
}
