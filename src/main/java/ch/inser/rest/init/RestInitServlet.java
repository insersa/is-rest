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

package ch.inser.rest.init;

import ch.inser.dynaplus.bo.BPFactory;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;

/**
 * Initialisation des services REST avec injection de ServiceLocator
 *
 * @author INSER SA *
 */
public class RestInitServlet extends org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher {

    /**
     * Generated
     */
    private static final long serialVersionUID = -6413761504431790906L;

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        servletConfig.getServletContext().setAttribute("VOFactory", VOFactory.getInstance());
        servletConfig.getServletContext().setAttribute("BPFactory", BPFactory.getInstance());
        servletConfig.getServletContext().setAttribute("ServiceLocator", ServiceLocator.getInstance());

        RestUtil.setServletContext(servletConfig.getServletContext());
    }

}
