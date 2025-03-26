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

package ch.inser.rest.auth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;

/**
 * Function to create and validate JSON Web Token
 *
 * @author INSER SA */
public class SecurityUtil {
    /** Définition de catégorie de logging */
    @SuppressWarnings("unused")
    private static final Log logger = LogFactory.getLog(SecurityUtil.class);

    /**
     * The implementation to use.
     */
    private static ISecurityImpl cSecurityImpl = new SecurityGenericImpl();

    /**
     * Constructeur
     */
    private SecurityUtil() {
    }

    /**
     * Get the token for a given user.
     *
     * @param aUser
     *            the user
     * @param aContextManger
     *            the context manager
     * @return the token
     */
    public static String getToken(ILoggedUser aUser, IContextManager aContextManger) {
        return cSecurityImpl.getToken(aUser, aContextManger);
    }

    /**
     * Get a new token for a given claims.
     *
     * @param aClaims
     *            the claims
     * @param aContextManger
     *            the context manager
     * @return the new token or <code>null</code> if the claims are <code>null</code>
     */
    public static String getToken(Claims aClaims, IContextManager aContextManger) {
        return cSecurityImpl.getToken(aClaims, aContextManger);
    }

    /**
     * Validate and get the token attributes.
     *
     * @param token
     *            the token value
     * @param aContextManager
     *            the context manager
     * @return the token attributes or <code>null</code> if the security token is disabled
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static Claims validateToken(String token, IContextManager aContextManager) throws ISSecurityException {
        return cSecurityImpl.validateToken(token, aContextManager);
    }

    /**
     * Get the user.
     *
     * @param aClaims
     *            the claims
     * @param aServiceLocator
     *            the service locator
     * @return the user
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    public static ILoggedUser getUser(Claims aClaims, ServiceLocator aServiceLocator) throws ISException {
        return cSecurityImpl.getUser(aClaims, aServiceLocator);
    }

    /**
     *
     * @param aServiceLocator
     *            service locator de l'application rest
     * @return objet LoggedUser public avec les droits limité de l'utilisateur publique
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    public static ILoggedUser getPublicUser(ServiceLocator aServiceLocator) throws ISException {
        return cSecurityImpl.getPublicUser(aServiceLocator);
    }

    /**
     * Validate and get the token attributes.
     *
     * @param token
     *            the token value
     * @param aKey
     *            the BASE64-encoded algorithm-specific signature verification key to use to validate any discovered JWS digital signature
     * @return the token attributes
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static Claims validateToken(String token, String aKey) throws ISSecurityException {
        return cSecurityImpl.validateToken(token, aKey);
    }

    /**
     * Set the security implementation to use.
     *
     * @param aSecurityImpl
     *            the security implementation
     */
    public static void setSecurityImpl(ISecurityImpl aSecurityImpl) {
        cSecurityImpl = aSecurityImpl;
    }
}
