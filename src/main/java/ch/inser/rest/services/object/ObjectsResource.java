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

import java.lang.reflect.Constructor;

import ch.inser.dynamic.util.VOInfo;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.RESTLocator;
import ch.inser.rest.util.ServiceLocator;

import io.swagger.annotations.Api;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;

/**
 * Service dynamique pour CRUD des objets métiers
 *
 * Les URLs disponibles sont:
 *
 * 1. objects/ pour la liste des noms des objets
 *
 * 2. objects/{objectname} pour la collection d'objets. ex. objects/buildings
 *
 * 3. objects/{objectname}/{id} pour un objet spécifique
 *
 * @author INSER SA *
 */
@Path("/objects")
@Api(value = "objects")
public class ObjectsResource {

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Donne la liste des objets métier disponibles dans l'application
     *
     * http://url/projet/services/objects/
     *
     * @return la liste de noms d'objets {'objects':['obj1','obj2']}
     */

    /**
     * Lien avec la ressource qui permet d'accéder aux objets métier par objectname
     *
     * Defines that the next path parameter after objects is treated as a parameter and passed to the ObjectNamesResource. Allows to type
     * http://url/projet/services/objects/{objectname}. {objectname} will be treated as parameter object and passed to ObjectNamesResource
     *
     * @param aObjectName
     *            nom de l'objet métier
     * @return ressource pour accéder aux objets métiers par nom
     * @throws ISException
     *             Erreur d'instantiation de la classe métier object name resource
     */
    @Path("{objectname}")
    public ObjectNamesResource getObjectNamesResource(@PathParam("objectname") String aObjectName) throws ISException {
        String onrClassName = (String) getVOInfo(aObjectName).getValue(VOInfo.REST_ONR_CLASSNAME);
        if (onrClassName == null) {
            return new ObjectNamesResource(iContext, aObjectName);
        }

        try {
            Class<?> cl = Class.forName(onrClassName);
            Constructor<?> constr = cl.getConstructor(ServletContext.class);
            return (ObjectNamesResource) constr.newInstance(iContext);
        } catch (Exception e) {
            throw new ISException("Error instantiating Class : " + onrClassName, e);
        }
    }

    /**
     *
     * @param aEntity
     *            nom de l'objet métier
     * @return la configuration REST d'un objet métier
     */
    private VOInfo getVOInfo(String aEntity) {
        return ((RESTLocator) ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getLocator("rest")).getVOInfo(aEntity);
    }

}
