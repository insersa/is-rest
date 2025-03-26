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

package ch.inser.rest.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.vo.IVOFactory;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants.Verb;

import io.jsonwebtoken.Claims;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Méthodes en commun entre les différents ressources REST
 *
 * @author INSER SA *
 */
public class RestUtil {

    /**
     * Private inplicit constructor
     */
    private RestUtil() {
        // Do nothing
    }

    /** Logger */
    private static final Log logger = LogFactory.getLog(RestUtil.class);

    /** Le rest servlet context */
    private static ServletContext iContext;

    /**
     * Get claims from token and push username into the context of the logger.
     *
     * Also verify that the object name is given. If the token or the object name is missing, no claims will be returned, and a bad request
     * response will be sent.
     *
     * It's important to push the username as early as possible to be able to log repeated misusage of the service by certain users. Each
     * service is responsible for cleaning the ndc at the end of the service call, in a finally clause.
     *
     * @param aToken
     *            security token
     * @param aObjectName
     *            object name
     * @return the claims encoded in the token
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static Claims getClaims(String aToken, String aObjectName) throws ISSecurityException {
        if (aToken == null) {
            logger.warn("Token absent: " + aObjectName);
            return null;
        }
        IContextManager ctx = getContextManager();
        Claims claims = SecurityUtil.validateToken(aToken, ctx);
        addToNdc(claims != null ? (String) claims.get("userName") : null);

        if (aObjectName == null) {
            logger.warn("Object name absent");
            return null;
        }
        if (!isResource(aObjectName)) {
            return null;
        }
        return claims;
    }

    /**
     * Get claims from token and push username into the context of the logger.
     *
     * It's important to push the username as early as possible to be able to log repeated misusage of the service by certain users. Each
     * service is responsible for cleaning the ndc at the end of the service call, in a finally clause.
     *
     * @param aToken
     *            security token
     * @return the claims encoded in the token
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static Claims getClaims(String aToken) throws ISSecurityException {
        IContextManager ctx = getContextManager();
        Claims claims = SecurityUtil.validateToken(aToken, ctx);
        addToNdc(claims != null ? (String) claims.get("userName") : null);
        return claims;
    }

    /**
     * Get claims from OIDC token
     *
     * It's important to push the username as early as possible to be able to log repeated misusage of the service by certain users. Each
     * service is responsible for cleaning the ndc at the end of the service call, in a finally clause.
     *
     * @param aToken
     *            OIDC security token
     * @return the claims encoded in the token
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static JwtClaims getClaimsOIDC(String aToken) throws ISSecurityException {
        return SecurityUtilOIDC.validateOIDCToken(aToken, getContextManager());
    }

    /**
     *
     * @param aValue
     *            value to add as a diagnostic context to the logs of this thread
     */
    public static void addToNdc(String aValue) {
        IContextManager ctx = getContextManager();
        if (ctx.getNdc() != null) {
            ctx.getNdc().push(aValue != null ? aValue : "!NULL!");
        }
    }

    /**
     * Adds the username to Ncd from an OIDC claims object
     *
     * @param aClaims
     *            OIDC claims
     */
    public static void addToNdc(JwtClaims aClaims) {
        IContextManager ctx = getContextManager();
        if (ctx.getNdc() == null) {
            return;
        }
        JsonObject claimsJSON = JsonUtil.stringToJsonObject(aClaims.getRawJson());
        if (claimsJSON == null || claimsJSON.isEmpty()) {
            return;
        }
        String username = claimsJSON.getString("preferred_username");
        ctx.getNdc().push(username != null ? username : "!NULL!");
    }

    /**
     *
     * @param aClaims
     *            user claims encoded in the security token
     * @param aObjectName
     *            ibject name
     * @param aHttpAction
     *            http action, ex. POST, GET
     * @return loggeduser with autority to perform the action on the business object
     * @throws ISException
     *             error retrieving user with claims
     */
    public static ILoggedUser getLoggedUser(Claims aClaims, String aObjectName, Verb aHttpAction) throws ISException {
        ILoggedUser loggedUser = SecurityUtil.getUser(aClaims, (ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR));
        return isAuthorized(loggedUser, aObjectName, aHttpAction) ? loggedUser : null;
    }

    /**
     *
     * @param aClaims
     *            user claims encoded in the security token
     * @param aObjectName
     *            ibject name
     * @param aHttpAction
     *            http action, ex. POST, GET
     * @return loggeduser with autority to perform the action on the business object
     * @throws ISException
     *             error retrieving user with claims
     */
    public static ILoggedUser getLoggedUser(JwtClaims aClaims, String aObjectName, Verb aHttpAction) throws ISException {
        ILoggedUser loggedUser = SecurityUtilOIDC.getUser(aClaims, (ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR));
        return isAuthorized(loggedUser, aObjectName, aHttpAction) ? loggedUser : null;
    }

    /**
     *
     * @param aUser
     *            user
     * @param aObjectName
     *            object name
     * @param aHttpAction
     *            action: POST, PUT, etc.
     * @return true if user has the authority to perform the action
     */
    public static boolean isAuthorized(ILoggedUser aUser, String aObjectName, Verb aHttpAction) {
        if (aUser == null) {
            logger.warn("Utilisateur non connu");
            return false;
        }

        // Contrôle métier
        if (!aUser.isAuthAction(aObjectName, aHttpAction.toString())) {
            logger.warn("Utilisateur non autorisé: " + aObjectName + "/" + aHttpAction);
            return false;
        }
        return true;
    }

    /**
     *
     * @param aClaims
     *            user claims encoded in the security token
     *
     * @return loggeduser corresponding to claims
     * @throws ISException
     *             error retrieving user with claims
     */
    public static ILoggedUser getLoggedUser(Claims aClaims) throws ISException {
        ILoggedUser loggedUser = SecurityUtil.getUser(aClaims, (ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR));
        if (loggedUser == null) {
            logger.warn("Utilisateur non connu: " + aClaims.get("userName"));
            return null;
        }
        return loggedUser;
    }

    /**
     *
     * @param aObjectName
     *            nom de l'objet métier
     * @return true si l'objet métier est accessible comme ressource REST
     */
    public static boolean isResource(String aObjectName) {
        boolean isResource = Boolean.TRUE.toString().equals(getVOInfo(aObjectName).getValue(VOInfo.REST_RESOURCE));
        if (!isResource) {
            logger.warn("Objet non-publié: " + aObjectName);
        }
        return isResource;
    }

    /**
     *
     * @param aContext
     *            Le rest servlet context with vo factory, service locator, bp factory
     */
    public static void setServletContext(ServletContext aContext) {
        iContext = aContext;
    }

    /**
     *
     * @param aEntity
     *            nom de l'objet métier
     * @return la configuration d'un objet métier
     */
    public static VOInfo getVOInfo(String aEntity) {
        return ((RESTLocator) ((ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR)).getLocator("rest")).getVOInfo(aEntity);
    }

    /**
     *
     * @param aObjectName
     *            nom de l'objet màtier
     * @return bp delegate pour un nom d'objet métier donné
     */
    public static IBPDelegate getBPDelegate(String aObjectName) {
        return (IBPDelegate) ((ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR)).getLocator("bp").getService(aObjectName);
    }

    /**
     * Remove the diagnostic context from the logs for this thread
     */
    public static void cleanNdc() {
        IContextManager contextManager = getContextManager();
        if (contextManager.getNdc() != null) {
            contextManager.getNdc().remove();
        }
    }

    /**
     *
     * @return le context manager
     */
    public static IContextManager getContextManager() {
        return ((ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR)).getContextManager();
    }

    /**
     *
     * @return Vo factory
     */
    public static IVOFactory getVOFactory() {
        return (VOFactory) iContext.getAttribute("VOFactory");
    }

    /**
     *
     * @param aResult
     *            résultat de mise à jour
     * @return réponse avec statut error (not found, conflict, ...) ou null selon résultat
     */
    public static Response getErrorResponse(IDAOResult aResult) {
        switch (aResult.getStatus()) {
            case NOT_FOUND:
                return Response.status(Status.NOT_FOUND).build();
            case CHANGED_TIMESTAMP:
                return Response.status(Status.CONFLICT).build();
            case NOTHING_TODO:
                return Response.status(Status.NOT_MODIFIED).build();
            case NO_RIGHTS:
                return Response.status(Status.UNAUTHORIZED).build();
            case KO:
                return Response.status(Status.BAD_REQUEST).build();
            default:
                return null;
        }
    }

    /**
     * Retrieves the OIDC token from the Authorization header "Bearer <token>"
     *
     * @param authorization
     *            header (e.g: Authorization: Bearer <token>)
     * @return the token
     *
     */
    public static String parseAuthorization(String authorization) {
        final String BEARER = "Bearer ";
        if (!authorization.startsWith(BEARER)) {
            return null;
        }
        return authorization.substring(BEARER.length());
    }

}
