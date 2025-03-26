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

import java.util.Base64;
import java.util.Date;
import java.util.Locale;

import javax.cache.Cache;
import javax.crypto.SecretKey;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.auth.SuperUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;

/**
 * The generic implementation.
 */
public class SecurityGenericImpl implements ISecurityImpl {

    /** Définition de catégorie de logging */
    @SuppressWarnings("unused")
    private static final Log logger = LogFactory.getLog(SecurityGenericImpl.class);

    /**
     * The property key name to indicate if the security token is enabled.
     */
    private static final String SECURITY_TOKEN = "security.token";

    /**
     * The property key name for the BASE64-encoded HS512 signing key to use to digitally sign the JWT
     */
    private static final String SECURITY_TOKEN_KEY = "security.token.key";

    @Override
    public String getToken(ILoggedUser aUser, IContextManager aContextManger) {
        ClaimsBuilder claimsBuilder = Jwts.claims();
        claimsBuilder.add("userId", aUser.getUserId().toString());
        claimsBuilder.add("userName", aUser.getUsername());
        claimsBuilder.add("status", aUser.getStatus().toString());
        Locale locale = aUser.getLocale();
        if (locale != null) {
            String language = locale.getLanguage();
            if (language != null) {
                claimsBuilder.add("lang", language);
            }
        }

        return getToken(claimsBuilder.build(), aContextManger);
    }

    @Override
    public String getToken(Claims aClaims, IContextManager aContextManager) {
        if (aClaims == null) {
            return null;
        }

        String securityTokenKey = aContextManager.getProperty(SECURITY_TOKEN_KEY);
        SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(securityTokenKey));

        Date now = new Date();
        JwtBuilder jwt = Jwts.builder().claims(aClaims).issuedAt(now).id((String) aClaims.get("userId"))
                .subject((String) aClaims.get("userName")).issuer(aContextManager.getApplicationName()).signWith(secretKey);

        // if it has been specified, let's add the expiration
        long aMillis = Long.parseLong(aContextManager.getProperty("security.timeout"));
        if (aMillis >= 0) {
            Date exp = new Date(now.getTime() + aMillis);
            jwt.expiration(exp);
        }

        return jwt.compact();
    }

    @Override
    public Claims validateToken(String token, IContextManager aContextManager) throws ISSecurityException {
        if ("false".equals(aContextManager.getProperty(SECURITY_TOKEN))) {
            return null;
        }
        return validateToken(token, aContextManager.getProperty(SECURITY_TOKEN_KEY));
    }

    @Override
    public ILoggedUser getUser(Claims aClaims, ServiceLocator aServiceLocator) throws ISException {

        // -- Get the user name
        String username = aClaims != null ? (String) aClaims.get("userName") : null;
        if (username != null && "true".equals(aServiceLocator.getContextManager().getProperty(SECURITY_SESSION_CONTROL))) {
            validateUserSession(username, aServiceLocator);
        }
        return getLoggedUser(username, aClaims, aServiceLocator);
    }

    /**
     * Vérifie que l'utilisateur a une session en cours dans la base de données: le timestamp logout est null
     *
     * @param aUsername
     *            nom d'utilisateur
     * @param aServiceLocator
     *            servicelocator avec accès à BP
     * @throws ISException
     *             erreur de consultation de table user
     */
    private void validateUserSession(String aUsername, ServiceLocator aServiceLocator) throws ISException {
        IDAOResult result = (IDAOResult) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser"))
                .executeMethode("validateSession", aUsername, new SuperUser());
        if (!result.isStatusOK()) {
            throw new ISSecurityException("User is not logged in: " + aUsername, null);
        }
    }

    @Override
    public ILoggedUser getPublicUser(ServiceLocator aServiceLocator) throws ISException {
        IContextManager ctx = aServiceLocator.getContextManager();
        String username = ctx.getProperty("security.public.user");
        if (username == null) {
            return null;
        }
        return getLoggedUser(username, null, aServiceLocator);
    }

    /**
     *
     * @param aUsername
     *            nom d'utilisateur
     * @param aClaims
     *            les attributs de l'utilisateur reçu dans le token
     * @param aServiceLocator
     *            service locator
     * @return objet LoggedUser de la caché ou initialisé la première fois
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    private ILoggedUser getLoggedUser(String aUsername, Claims aClaims, ServiceLocator aServiceLocator) throws ISException {
        // -- Gestion du retour du ILoggedUser par le cache
        IContextManager contextManager = aServiceLocator.getContextManager();
        Object initData = aUsername;
        if (aClaims != null && Boolean.TRUE.toString().equals(contextManager.getProperty("security.inituser.claims"))) {
            // Initialise le logged user à la base des claims
            initData = aClaims;
        }
        if ("true".equals(contextManager.getProperty(SECURITY_USER_CACHE))) {
            Cache<String, ILoggedUser> cache = contextManager.getCacheManager().getCache("userCache", String.class, ILoggedUser.class);

            if (!cache.containsKey(aUsername)) {

                ILoggedUser user = (ILoggedUser) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser"))
                        .executeMethode("initLoggedUser", initData, new SuperUser());
                cache.put(aUsername, user);
            }
            return cache.get(aUsername);
        }

        // -- Retour de l'utilisateur si configuration sans cache
        return (ILoggedUser) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser")).executeMethode("initLoggedUser",
                initData, new SuperUser());
    }

    @Override
    public Claims validateToken(String token, String aKey) throws ISSecurityException {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(aKey));
            JwtParser parser = Jwts.parser().verifyWith(secretKey).build();
            return parser.parseSignedClaims(token).getPayload();
        } catch (UnsupportedJwtException e) {
            throw new ISSecurityException("The token is not a valid JWS", e);
        } catch (MalformedJwtException e) {
            throw new ISSecurityException("The token is malformed", e);
        } catch (ExpiredJwtException e) {
            throw new ISSecurityException("The token has expired", e);
        } catch (IllegalArgumentException e) {
            throw new ISSecurityException("The token is null or empty", e);
        }
    }

}
