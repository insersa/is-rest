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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Resource that provides the short and long help texts for the tooltips of input labels.
 *
 * @author INSER SA *
 */
@Path("/help")
@Api(value = "help")
public class HelpResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(HelpResource.class);

    /**
     * The rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * @param aToken
     *            le token
     * @return HTTP response with short and long help texts for input labels
     */
    @ApiOperation(value = "Get the permissions for the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 403, message = "The rights are not sufficient to access data"),
            @ApiResponse(code = 500, message = "Error querying the permissions tables") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getHelpTexts(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken) {

        try {
            logger.debug("Get help texts");

            if (aToken == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Security
            IContextManager ctx = RestUtil.getContextManager();
            Claims claims = RestUtil.getClaims(aToken);
            ILoggedUser loggedUser = SecurityUtil.getUser(claims, (ServiceLocator) iContext.getAttribute("ServiceLocator"));

            // Help texts
            JsonObject json = createHelpJson(loggedUser);

            // Build the response
            return Response.ok(json.toString()).header("token", SecurityUtil.getToken(claims, ctx)).build();
        } catch (ISSecurityException e) {
            logger.warn("Invalid token", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Error fetching help texts", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Creates a json object with the short and long help texts for input labels
     *
     * {<label key>: {<iso lang code>: {short:<short text>, long: <long text in HTML>}, <iso lang code 2>: {...}}, ....}
     *
     * @param aUser
     *            authentified user
     * @return json object with help texts in all languages
     * @throws ISException
     *             error querying the help tables
     */
    @SuppressWarnings("unchecked")
    private JsonObject createHelpJson(ILoggedUser aUser) throws ISException {
        Map<String, Object> helpTexts = new HashMap<>();
        List<IValueObject> vos = getHelpTexts(aUser);
        for (IValueObject vo : vos) {
            if (vo.getProperty("htx_short_text") == null && vo.getProperty("htx_long_text") == null) {
                continue;
            }
            Map<String, String> texts = new HashMap<>();
            texts.put("short", (String) vo.getProperty("htx_short_text"));
            texts.put("long", (String) vo.getProperty("htx_long_text"));
            Map<String, Object> langs = (Map<String, Object>) helpTexts.get(vo.getProperty("hke_name"));
            if (langs == null) {
                langs = new HashMap<>();
                helpTexts.put((String) vo.getProperty("hke_name"), langs);
            }
            langs.put((String) vo.getProperty("htx_iso_lang_code"), texts);
        }
        return JsonUtil.mapToJsonObject(helpTexts).build();
    }

    /**
     * @param aUser
     *            authentified user
     * @return help texts
     * @throws ISException
     *             error querying the help tables
     */
    private List<IValueObject> getHelpTexts(ILoggedUser aUser) throws ISException {
        IValueObject qVo = getQueryVO();
        return ((IBPDelegate) ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getLocator("bp")
                .getService(Entity.HELPTEXT.toString())).getList(qVo, aUser, new DAOParameter(Name.ROWNUM_MAX, 0)).getListObject();
    }

    /**
     *
     * @return Query vo for fetching the help texts
     */
    protected IValueObject getQueryVO() {
        return ((VOFactory) iContext.getAttribute("VOFactory")).getVO(Entity.HELPTEXT.toString());
    }

}
