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

package ch.inser.rest.oidc.auth;

import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.util.ServiceLocator;

/**
 * Pour permettre de modifier l'implémentation de SecurityUtil OIDC en cas de besoin
 *
 * @see ch.inser.rest.auth.SecurityUtil
 */
public interface ISecurityImplOIDC {

    /**
     * The property key to indicate if the user cache is enabled.
     */
    public static final String SECURITY_USER_CACHE = "security.user.cache";

    /**
     * The property key to indicate if a tokens validity should be checked against login/logout timestamps
     */
    public static final String SECURITY_SESSION_CONTROL = "security.session.control";

    /**
     * Validate and get the OIDC token attributes.
     *
     * @param aToken
     *            the OIDC token
     * @param aContextManager
     *            the context manager
     * @return the token attributes or <code>null</code> if the security token is disabled
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public JwtClaims validateOIDCToken(String aToken, IContextManager aContextManager) throws ISSecurityException;

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
    public ILoggedUser getUser(JwtClaims aClaims, ServiceLocator aServiceLocator) throws ISException;

    /**
     * Validate and get the token attributes.
     *
     * @param aToken
     *            the OIDC token
     * @param aCertUrl
     *            url of certificate that validates the token
     * @param aAudience
     *            the application name to verify in the list of authorized applications
     * @param aAzp
     *            Authorized party (used instead of audience for public applications without roles
     * @return the token attributes
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public JwtClaims validateOIDCToken(String aToken, String aCertUrl, String aAudience, String aAzp) throws ISSecurityException;
}
