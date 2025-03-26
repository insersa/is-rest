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

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;

/**
 * Pour permettre de modifier l'implémentation de SecurityUtil en cas de besoin
 *
 * @see SecurityUtil
 * @author INSER SA */
public interface ISecurityImpl {

    /**
     * The property key to indicate if the user cache is enabled.
     */
    public static final String SECURITY_USER_CACHE = "security.user.cache";

    /**
     * The property key to indicate if a tokens validity should be checked against login/logout timestamps
     */
    public static final String SECURITY_SESSION_CONTROL = "security.session.control";

    /**
     * Get the token for a given user.
     *
     * @param aUser
     *            the user
     * @param aContextManger
     *            the context manager
     * @return the token
     */
    public String getToken(ILoggedUser aUser, IContextManager aContextManger);

    /**
     * Get a new token for a given claims.
     *
     * @param aClaims
     *            the claims
     * @param aContextManger
     *            the context manager
     * @return the new token or <code>null</code> if the claims are <code>null</code>
     */
    public String getToken(Claims aClaims, IContextManager aContextManger);

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
    public Claims validateToken(String token, IContextManager aContextManager) throws ISSecurityException;

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
    public ILoggedUser getUser(Claims aClaims, ServiceLocator aServiceLocator) throws ISException;

    /**
     *
     * @param aServiceLocator
     *            service locator de l'application rest
     * @return objet LoggedUser public avec les droits limité de l'utilisateur publique
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    public ILoggedUser getPublicUser(ServiceLocator aServiceLocator) throws ISException;

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
    public Claims validateToken(String token, String aKey) throws ISSecurityException;

}
