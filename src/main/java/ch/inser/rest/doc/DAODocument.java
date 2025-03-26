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

package ch.inser.rest.doc;

import java.util.Set;

import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.dao.GenericDataAccessObject;

/**
 * DAO pour objet métier Document
 *
 * Les blobs ne sont pas mis à jour dans le update de l'objet
 *
 * @author INSER SA *
 */
public class DAODocument extends GenericDataAccessObject {

    /**
     *
     */
    private static final long serialVersionUID = -2018314234244849899L;

    /**
     * Constructeur
     *
     * @param aVOInfo
     *            le vo
     */
    public DAODocument(VOInfo aVOInfo) {
        super("Document", aVOInfo);
    }

    @Override
    public Set<String> getListUpdateFields() {
        Set<String> updateFields = super.getListUpdateFields();
        // Supression dans la liste des champs updatable les champs du lob pour
        // ne pas qu'il soit set à null
        updateFields.remove("doc_blob");

        return updateFields;
    }

}