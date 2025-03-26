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

package ch.inser.rest.core;

import java.io.ByteArrayInputStream;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.RestUtil;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

/**
 * Abstract class for file uploads and downloads
 */
public abstract class AbstractFileItemResource {

    /** The rest servlet context */
    @Context
    protected ServletContext iContext;

    /** Id of business object */
    protected String iId;

    /**
     * Constructor called by FileResource
     *
     * @param aContext
     *            rest servlet context
     * @param aId
     *            id document id
     */
    public AbstractFileItemResource(ServletContext aContext, String aId) {
        iContext = aContext;
        iId = aId;
    }

    /**
     * Downloads a file
     *
     * @param aUser
     *            logged user
     * @param aObjName
     *            object name - optional, by default Document
     * @param aFieldName
     *            fieldname - optiona, by default doc_blob
     * @return file stream
     * @throws ISException
     *             error retrieving document
     */
    protected Response downloadFile(ILoggedUser aUser, String aObjName, String aFieldName) throws ISException {
        // If the id is not a number, an internal server error is returned
        Long id = Long.valueOf(iId);

        // -- Search the file
        IBPDelegate bp = RestUtil.getBPDelegate(Entity.DOCUMENT.toString());
        DAOParameter[] objParams = null;
        if (aObjName != null) {
            objParams = new DAOParameter[] { new DAOParameter("objectName", aObjName), new DAOParameter("fieldName", aFieldName) };
        }
        IValueObject rec = bp.getRecord(id, aUser, objParams).getValueObject();

        // If the file doesn't exist or it's in the upload temp folder, or if the user doesn't have right to access the parent VO -> return
        // NOT FOUND
        if (rec == null || "UPLOAD".equals(rec.getProperty("doc_obj_name"))
                || aObjName == null && bp.executeMethode("getVOParent", rec, aUser) == null
                || aObjName != null && RestUtil.getBPDelegate(aObjName).getRecord(id, aUser).getValueObject() == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        ResponseBuilder response = null;

        /*
         * THe octet content of the file depends on how it's stored. In database mode, the content is ready in the field doc_blob_byte (the
         * content of the record blob is no longer available after consulting the record). In network drive mode, the content is converted
         * to bytes with the method getDocContents
         */
        if ("true".equals(RestUtil.getContextManager().getProperty("document.database"))) {
            // Json response
            response = Response.ok(new ByteArrayInputStream((byte[]) rec.getProperty("doc_blob_byte")));
            response.header("Content-Disposition", "attachment;filename=\"" + rec.getProperty("doc_c_nomfichier") + "\"");
            response.type((String) rec.getProperty("doc_mimetype"));
        } else {
            // Retrieve file content
            byte[] contents = (byte[]) bp.executeMethode("getDocContents", rec, aUser);
            // Json response
            response = Response.ok(new ByteArrayInputStream(contents));
            response.header("Content-Disposition", "attachment;filename=\"" + rec.getProperty("doc_filename") + "\"");
            response.type((String) rec.getProperty("doc_mimetype"));
        }

        return response.build();
    }

}
