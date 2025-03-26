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

package ch.inser.rest.code;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;

import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.dao.GenericDataAccessObject;
import ch.inser.dynaplus.util.Constants.Entity;

/**
 * DAO pour chercher les libellés multilingue associés au code
 *
 * @author INSER SA *
 */
public class DAOCode extends GenericDataAccessObject {

    /**
     * UID
     */
    private static final long serialVersionUID = 415420266160478338L;

    /**
     *
     * @param aVOInfo
     *            vo info de Help key
     */
    public DAOCode(VOInfo aVOInfo) {
        super(Entity.CODE.toString(), aVOInfo);
    }

    @Override
    public IDAOResult getRecord(Object id, ILoggedUser user, Connection connection) throws SQLException {
        return super.getRecordFull(id, user, connection);
    }

    @Override
    protected void addNames(Collection<String> aNames, StringBuilder aSql) {

        Iterator<String> it = aNames.iterator();
        while (it.hasNext()) {
            String str = it.next();

            if ("textcourt_fr".equals(str)) {
                aSql.append("(SELECT ctx_textcourt from t_codetext where ctx_cod_id=cod_id and ctx_lang='fr') textcourt_fr ");
            } else if ("textlong_fr".equals(str)) {
                aSql.append("(SELECT ctx_textlong from t_codetext where ctx_cod_id=cod_id and ctx_lang='fr') textlong_fr ");
            } else if ("textcourt_de".equals(str)) {
                aSql.append("(SELECT ctx_textcourt from t_codetext where ctx_cod_id=cod_id and ctx_lang='de') textcourt_de ");
            } else if ("textlong_de".equals(str)) {
                aSql.append("(SELECT ctx_textlong from t_codetext where ctx_cod_id=cod_id and ctx_lang='de') textlong_de ");
            } else {
                aSql.append(str);
            }
            if (it.hasNext()) {
                aSql.append(", ");
            }
        }
    }

}
