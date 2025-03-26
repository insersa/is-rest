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

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.DigestException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.ILoggedUser.Status;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.vo.IVOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.tools.SecurityTools;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.Constants;
import ch.inser.rest.util.Constants.Entity;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Service pour authentification d'un utilisateur
 *
 * @author INSER SA */
@Path("/token")
@Api(value = "token")
public class TokenResource {

    /** Définition de catégorie de logging */
    private static final Log logger = LogFactory.getLog(TokenResource.class);

    /** Context avec les objects pour accéder à la BD */
    @Context
    private ServletContext iContext;

    /**
     * @param authData
     *            login et mot de passe
     * @param req
     *            the request
     * @return token
     */
    @ApiOperation(value = "Get the token for an user")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Input is a bad json format"),
            @ApiResponse(code = 401, message = "User not authorized for login"),
            @ApiResponse(code = 500, message = "Unexpected error while login the user") })
    @POST()
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@ApiParam(value = "username and password", required = true) String authData, @Context HttpServletRequest req) {

        try (JsonReader reader = Json.createReader(new StringReader(authData))) {
            // -- Transformation des données d'entrée en objet JSON
            JsonObject authJson = reader.readObject();
            if (authJson.get("username") != null) {
                RestUtil.addToNdc(authJson.get("username").toString());
            }
            if (authJson.get("username") == null || authJson.get("password") == null) {
                return sendUnauthorized();
            }

            // -- Paramètres nécessaires pour l'authentification
            ServiceLocator serviceLocator = (ServiceLocator) iContext.getAttribute("ServiceLocator");
            IContextManager contextManager = serviceLocator.getContextManager();
            ILoggedUser superUser = serviceLocator.getSuperUser();
            IBPDelegate dbLoggedUser = RestUtil.getBPDelegate(Entity.LOGGED_USER.toString());

            // -- Validation de l'authentification de l'utilisateur
            Status userStatus;
            if ("realm".equals(contextManager.getProperty("security.mode"))) {
                // Sécurité par REALM (annuaire comme LDAP etc..)
                userStatus = valideUserRealm(authJson, req);
            } else {
                // Sécurité dans la base de données de l'application (système
                // par défaut)
                userStatus = valideUserBd(authJson, superUser, dbLoggedUser);
            }

            // -- Création de l'utilisateur
            if (userStatus == Status.VALID || userStatus == Status.INITIAL_LOGON) {
                // Initialiser l'utilisateur
                ILoggedUser validUser = (ILoggedUser) dbLoggedUser.executeMethode("initLoggedUser", authJson.getString("username"),
                        superUser);
                // Valider l'utilisateur et retourner l'information au client
                if (validUser != null) {
                    validUser.setStatus(userStatus);

                    // Put the user in the cache
                    contextManager.getCacheManager().getCache("userCache", String.class, ILoggedUser.class).put(validUser.getUsername(),
                            validUser);
                    logger.info("Login de l'utilisateur " + validUser.getUsername());
                    JsonObjectBuilder resultJson = Json.createObjectBuilder().add("success", true).add("token",
                            SecurityUtil.getToken(validUser, contextManager));
                    return Response.ok(resultJson.build().toString()).build();
                }

            }

            // -- En cas de non réponse jusqu'ici l'utilisateur n'est pas
            // autorisé
            return sendUnauthorized();

        } catch (JsonException e) {
            logger.error("Bad format input parameter", e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Login failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Contrôle de l'autorisation de l'utilisateur par la db. Mise du mode securite.mode=db dans le fichier .properties du projet
     *
     * @param aAuthJson
     *            <code>json</code> contenant le login et le password
     * @param aSuperUser
     *            Utilisateur pour vérifier ces donneés dans la db
     * @param aDbLoggedUser
     *            Accès au BP de l'objet LoggedUser
     * @return <code>Status</code> information minimum à retourner, le statut doit être uniquement <code>UNAUTHORIZED</code> ou
     *         <code>VALIDE</code>
     * @throws DigestException
     *             if an error occurs
     * @throws NoSuchAlgorithmException
     *             Erreur lié à l'algorithme
     * @throws UnsupportedEncodingException
     *             UnsupportedEncodingException
     * @throws ISException
     *             erreur d'execution
     */
    protected Status valideUserBd(JsonObject aAuthJson, ILoggedUser aSuperUser, IBPDelegate aDbLoggedUser)
            throws UnsupportedEncodingException, NoSuchAlgorithmException, DigestException, ISException {
        // -- Paramètres nécessaires pour l'authentification
        ServiceLocator serviceLocator = (ServiceLocator) iContext.getAttribute("ServiceLocator");
        IContextManager contextManager = serviceLocator.getContextManager();

        IValueObject vo = ((IVOFactory) iContext.getAttribute("VOFactory")).getVO("LoggedUser");
        vo.setProperty("username", aAuthJson.getString("username"));
        if (contextManager.getProperty("security.hashMethode") != null) {
            vo.setProperty("password", aAuthJson.getString("password"));
        } else {
            vo.setProperty("password", SecurityTools.encryptString(aAuthJson.getString("password")));
        }

        return (Status) aDbLoggedUser.executeMethode("validateUser", vo, aSuperUser);
    }

    /**
     * Contrôle de l'utilsiateur avec le realm, sécurité connecté à un annuaire tel que LDAP. La configuration de ce mode se fait dans le
     * fichier .properties. La valeur est la suivante <code>security.mode=realm</code>.
     *
     * ATTENTION FONCTIONNE UNIQUEMENT AVEC SERVLET-API > 3.0 (depuis tomcat 7)
     *
     * @param aAuthJson
     *            <code>json</code> contenant le login et le password
     * @param aReq
     *            Accès au HttpServletRequest, va fournir l'accord d'accès pour un utilisateur
     * @return <code>Status</code> information minimum à retourner, le statut doit être uniquement <code>UNAUTHORIZED</code> ou
     *         <code>VALIDE</code>
     */
    protected Status valideUserRealm(JsonObject aAuthJson, HttpServletRequest aReq) {

        Status userStatus;

        try {
            // only login if not already logged in...
            if (aReq.getUserPrincipal() == null) {

                aReq.login(aAuthJson.getString("username"), aAuthJson.getString("password"));
                aReq.getServletContext().log(aAuthJson.getString("username") + " successfully logged in.");
            } else {
                logger.info("Skip logged because already logged in: " + aAuthJson.getString("login"));
            }

            // Personne autorisée
            userStatus = Status.VALID;

        } catch (ServletException e) {
            logger.warn("Login failed", e);
            // En cas d'exception l'utilisateur n'est pas autorisé
            userStatus = Status.UNAUTHORIZED;
        }

        return userStatus;
    }

    /**
     * Send an UNAUTHORIZED (401) response
     *
     * @return the response
     */
    protected Response sendUnauthorized() {
        // Création de la réponse
        JsonObjectBuilder resultJson = Json.createObjectBuilder().add("success", false);

        return Response.status(Response.Status.UNAUTHORIZED).entity(resultJson.build().toString()).build();
    }

    /**
     * Processes the logout event in the backend, for example records the time of logout. The actual logout happens at the stateless client
     * -> the token is deleted from the session storage
     *
     * @param aToken
     *            the security token with user name and id
     * @return response OK (200), BAD REQUEST (400), UNAUTHORIZED (401) or ERROR (500)
     */
    @ApiOperation(value = "Get the token for an user")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 401, message = "User not authorized for logout"),
            @ApiResponse(code = 500, message = "Unexpected error while login the user") })
    @DELETE()
    public Response logout(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken) {
        try {
            if (aToken == null) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurite
            Claims claims = RestUtil.getClaims(aToken);
            ILoggedUser loggedUser = SecurityUtil.getUser(claims, (ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR));

            // Logout
            RestUtil.getBPDelegate(Entity.LOGGED_USER.toString()).executeMethode("logout", null, loggedUser);
            logger.info("Logout de l'utilisateur " + loggedUser.getUsername());
            return Response.ok().build();
        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login", e);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Erreur", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

}
