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

package ch.inser.rest.services;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.CodeAlertLevel;
import ch.inser.dynaplus.util.Constants.CodeBoolean;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Ressource qui fourni des messages d'alert aux utilisateurs au moment de login
 *
 * Implémentation example (standard)
 *
 * @author INSER SA *
 */
@Path("/alert")
@Api(value = "alert")
public class AlertResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(AlertResource.class);

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * Get the currently active alert message (title, text, alert level), if there is any
     *
     * @param aToken
     *            the security token
     * @param aLang
     *            the language of the alert message
     * @return the alert object as JSON
     */
    @ApiOperation(value = "Get the current alert message")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 500, message = "Error querying the codes tables") })
    @GET
    @Path("{lang}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getAlert(@HeaderParam("token") String aToken, @PathParam("lang") String aLang) {
        try {
            logger.debug("GET alert message. Lang: " + aLang);

            // Check the token
            Claims claims = RestUtil.getClaims(aToken);
            if (claims == null || aLang == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Check the user authorizations
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Get the alert message
            IValueObject vo = getAlert(aLang, loggedUser);

            // Build the response
            JsonObject json = JsonVoUtil.voToJson(vo, false, loggedUser);

            return Response.ok(json.toString()).header("token", SecurityUtil.getToken(claims, RestUtil.getContextManager())).build();
        } catch (ISSecurityException e) {
            logger.warn("Security error", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Error", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     *
     * @param aLang
     *            language of alert message
     * @param aUser
     *            logged user
     * @return vo with attributes: id, title, text, level ("info" or "warn")
     * @throws ISException
     *             error consulting the alert message table
     */
    private IValueObject getAlert(String aLang, ILoggedUser aUser) throws ISException {
        IValueObject qVo = RestUtil.getVOFactory().getVO("AlertText");
        qVo.setProperty("ale_active", CodeBoolean.OUI.getValue());
        qVo.setProperty("alt_lang", aLang);

        List<IValueObject> list = RestUtil.getBPDelegate(Entity.ALERTTEXT.toString()).getList(qVo, aUser).getListObject();
        if (list.isEmpty()) {
            // Empty vo
            return RestUtil.getVOFactory().getVO("AlertText");
        }
        IValueObject vo = list.get(0);

        // Standardize the attribute names for the front end
        vo.setProperty("id", vo.getProperty("alt_ale_id"));
        vo.setProperty("title", vo.getProperty("alt_title"));
        vo.setProperty("text", vo.getProperty("alt_text"));
        vo.setProperty("level", CodeAlertLevel.parse((Long) vo.getProperty("ale_level_code")).toString());

        return vo;
    }

    /**
     *
     * @return Nom de l'obet métier code, par défaut "Code"
     */
    protected String getEntity() {
        return Entity.ALERTTEXT.toString();
    }
}
