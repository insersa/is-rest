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

package ch.inser.rest.provider;

import java.io.IOException;

import ch.inser.rest.util.ServiceLocator;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * Entêtes à mettre par défaut pour la sécurité de l'application selon standard OWASP
 *
 *
 * "X-Content-Type-Options": Lorsque la valeur de cet en-tête est "nosniff", le navigateur web ne devine plus le type MIME du fichier par
 * sniffing. Si cette option n'est pas activée, cela entraîne un risque accru de cross-site scripting.
 *
 * "X-XSS-Protection": Cet en-tête est utilisé par les navigateurs modernes afin d'activer leur filtre intégré anti cross-site scripting.
 * S'il est activé, mais sans "mode=block", cela entraîne un risque accru que des failles de type cross-site scripting normalement
 * non-exploitables puissent être exploitées.
 *
 * "X-Frame-Options": Cet en-tête peut être utilisé pour indiquer si un navigateur est autorisé à afficher une page à l'intérieur d'un
 * <frame> ou <iframe>. Les options valides sont DENY, ce qui empêche la page d'exister dans un cadre ou SAMEORIGIN pour permettre le
 * framing, mais seulement par l'hôte d'origine. Quand cette option n'est pas activée, le site court un risque accru de click-jacking, sauf
 * si d'autres protections sont activées au niveau applicatif. *
 *
 * @author INSER SA *
 */
public class NoSniffFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext aRequestContext, ContainerResponseContext aResponseContext) throws IOException {
        if (Boolean.TRUE.toString().equalsIgnoreCase(ServiceLocator.getInstance().getContextManager().getProperty("environment.dev"))) {
            return;
        }
        MultivaluedMap<String, Object> headers = aResponseContext.getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-XSS-Protection", "1; mode=block");
        headers.add("X-Frame-Options", "DENY");
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.add("Content-Security-Policy", "default-src 'self'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; "
                + "upgrade-insecure-requests; block-all-mixed-content");
        headers.add("Referrer-Policy", "same-origin");

    }
}
