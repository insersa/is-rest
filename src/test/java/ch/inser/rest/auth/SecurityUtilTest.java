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

import static org.junit.Assert.assertNotNull;

import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.Test;

import ch.inser.dynamic.common.ILoggedUser.Status;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Test the security utilities class.
 *
 * @author INSER SA
 */
public class SecurityUtilTest {

    /**
     * Generate and show a BASE64-encoded algorithm-specific signature verification key to use to validate any discovered JWS digital
     * signature.
     *
     * @throws NoSuchAlgorithmException
     *             the exceptions
     */
    @Test // Used only to produce a new key
    public void generateKey() throws NoSuchAlgorithmException {
        SecretKey secretKey = Jwts.SIG.HS512.key().build();
        System.out.println(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        assertNotNull(secretKey);
    }

    /**
     * Test the JWT.
     */
    @Test
    public void testJWT() {
        ClaimsBuilder claimsBuilder = Jwts.claims();
        claimsBuilder.add("userId", "Test");
        claimsBuilder.add("userName", "Test");
        claimsBuilder.add("status", Status.VALID.toString());
        String token = generateJwtToken(claimsBuilder.build());
        assertNotNull(token);
        System.out.println(token);
        printStructure(token);
        printBody(token);
    }

    /**
     * Generate a JWT token.
     *
     * @param aClaims
     *            the claims
     * @return the token
     */
    private String generateJwtToken(Claims aClaims) {
        Date now = new Date();
        SecretKey secretKey = Keys
                .hmacShaKeyFor("TRNKpoTfEJlJv3Mp0fDtSWLlgAFhHK+WX6aA/c/lh/RFGoiuztFDtzNJfP44UDxa/HZUtrecMLjPOztNKtfPjw==".getBytes());

        JwtBuilder jwtBuilder = Jwts.builder().claims(aClaims).id((String) aClaims.get("userId")).issuedAt(now)
                .subject((String) aClaims.get("userName")).issuer("INSER SA").signWith(secretKey, Jwts.SIG.HS512);

        Date exp = new Date(now.getTime() + 3600000);
        jwtBuilder.expiration(exp);
        return jwtBuilder.compact();
    }

    /**
     * Print on Syste.out the token header, body and signature.
     *
     * @param token
     *            the token
     */
    private void printStructure(String token) {
        SecretKey secretKey = Keys
                .hmacShaKeyFor("TRNKpoTfEJlJv3Mp0fDtSWLlgAFhHK+WX6aA/c/lh/RFGoiuztFDtzNJfP44UDxa/HZUtrecMLjPOztNKtfPjw==".getBytes());

        Jws<Claims> signedJWT = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);

        System.out.println("Header     : " + signedJWT.getHeader());
        System.out.println("Body       : " + signedJWT.getPayload());
        System.out.println("Signature  : " + signedJWT.getSignature());
    }

    /**
     * Print on Syste.out the token issuer, subject and expiration.
     *
     * @param token
     *            the token
     */
    private void printBody(String token) {
        SecretKey secretKey = Keys
                .hmacShaKeyFor("TRNKpoTfEJlJv3Mp0fDtSWLlgAFhHK+WX6aA/c/lh/RFGoiuztFDtzNJfP44UDxa/HZUtrecMLjPOztNKtfPjw==".getBytes());

        Claims body = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

        System.out.println("Issuer     : " + body.getIssuer());
        System.out.println("Subject    : " + body.getSubject());
        System.out.println("Expiration : " + body.getExpiration());
    }
}
