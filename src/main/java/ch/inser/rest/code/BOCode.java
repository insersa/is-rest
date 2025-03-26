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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.DAOResult;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.IDAOResult.Status;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.bo.AbstractBusinessObject;
import ch.inser.dynaplus.bo.IBusinessObject;
import ch.inser.dynaplus.util.Constants.Entity;

/**
 * BO pour gestion de codes
 *
 * @author INSER SA *
 */
public class BOCode extends AbstractBusinessObject {

    /**
     * UID
     */
    private static final long serialVersionUID = 4958042807787129928L;

    /** Définition de la catégorie de logging */
    private static final Log logger = LogFactory.getLog(BOCode.class);

    /**
     *
     * @param aVOInfo
     *            config de Code
     */
    public BOCode(VOInfo aVOInfo) {
        super(Entity.CODE.toString(), aVOInfo);
    }

    @Override
    public IDAOResult update(IValueObject valueObject, Connection con, ILoggedUser user) throws SQLException {
        IDAOResult results = updateCodetexts(valueObject, con, user);
        if (results.isStatusOK() && !results.isStatusNOTHING_TODO()) {
            return results;
        }

        IDAOResult result = super.update(valueObject, con, user);
        if (!result.isStatusOK() && !result.isStatusNOTHING_TODO()) {
            logger.error("Erreur d'enregistrement de code. Res: " + result + ". Vo: " + valueObject.getProperties());
            return result;
        }

        results.getListObject().add(result.getValueObject());
        return new DAOResult(results.getListObject());
    }

    /**
     * Met à jour les libellés des codes
     *
     * @param aVo
     *            vo Code avec libellés par langue
     * @param aCon
     *            connexion
     * @param aUser
     *            utilisateur
     * @return nbr d'enreistrement modifiés
     * @throws SQLException
     *             erreur de consultation ou enregistrement de codetexts
     */
    protected IDAOResult updateCodetexts(IValueObject aVo, Connection aCon, ILoggedUser aUser) throws SQLException {
        IBusinessObject bo = getBOFactory().getBO(Entity.CODETEXT);
        IValueObject qVo = getVOFactory().getVO(Entity.CODETEXT);
        qVo.setProperty("ctx_cod_id", aVo.getId());
        List<Object> ids = bo.getFieldsRequest(qVo, "ctx_id", aCon).getListValue();
        List<IValueObject> results = new ArrayList<>(ids.size());
        for (Object id : ids) {
            IValueObject rec = bo.getRecord(id, aCon, aUser, false).getValueObject();
            rec.setProperty("ctx_textcourt", aVo.removeProperty("textcourt_" + rec.getProperty("ctx_lang")));
            rec.setProperty("ctx_textlong", aVo.removeProperty("textlong_" + rec.getProperty("ctx_lang")));
            IDAOResult result = bo.update(rec, aCon, aUser);
            if (!result.isStatusOK() && !result.isStatusNOTHING_TODO()) {
                logger.error("Erreur d'enregistrement de codetext. Res: " + result + ". Vo: " + rec.getProperties());
                return result;
            }
            if (result.getNbrRecords() > 0) {
                results.add(result.getValueObject());
            }
        }
        return new DAOResult(results);
    }

    @Override
    public IDAOResult create(IValueObject valueObject, Connection con, ILoggedUser user) throws SQLException {
        List<Object> langs = getLanguages(con);
        IValueObject voOrig = (IValueObject) valueObject.clone();
        for (Object lang : langs) {
            valueObject.removeProperty("textcourt_" + lang);
        }
        IDAOResult result = super.create(valueObject, con, user);
        if (!result.isStatusOK()) {
            logger.error("Erreur de création de code. Vo: " + valueObject.getProperties());
            return result;
        }
        voOrig.setId(result.getId());
        List<Object> ids = createCodeTexts(voOrig, langs, con, user);
        if (ids.isEmpty()) {
            return new DAOResult(Status.NOT_FOUND);
        }

        return result;
    }

    /**
     *
     * @param aCon
     *            connexion
     * @return les langues (iso lang code) disponibles dans la table de contextes
     * @throws SQLException
     *             erreur de consultation de la table de codetexts
     */
    private List<Object> getLanguages(Connection aCon) throws SQLException {
        IValueObject qVo = getVOFactory().getVO(Entity.CODETEXT);
        qVo.setProperty("DISTINCT", true);
        return getBOFactory().getBO(Entity.CODETEXT).getFieldsRequest(qVo, "ctx_lang", aCon).getListValue();
    }

    /**
     *
     * @param aVo
     *            co code avec libellés par langue
     * @param aLangs
     *            liste de langues
     * @param aCon
     *            connexion
     * @param aUser
     *            utilisateur
     * @return liste d'ids de codetexts crées
     * @throws SQLException
     *             erreur de création
     */
    protected List<Object> createCodeTexts(IValueObject aVo, List<Object> aLangs, Connection aCon, ILoggedUser aUser) throws SQLException {
        List<Object> ids = new ArrayList<>();
        for (Object lang : aLangs) {
            IValueObject vo = getVOFactory().getVO(Entity.CODETEXT);
            vo.setProperty("ctx_cod_id", aVo.getId());
            vo.setProperty("ctx_lang", lang);
            vo.setProperty("ctx_textcourt", aVo.getProperty("textcourt_" + lang));
            if (aVo.getProperty("textlong_" + lang) != null) {
                vo.setProperty("ctx_textlong", aVo.getProperty("textlong_" + lang));
            } else {
                vo.setProperty("ctx_textlong", aVo.getProperty("textcourt_" + lang));
            }
            IDAOResult result = getBOFactory().getBO(Entity.CODETEXT).create(vo, aCon, aUser);
            if (!result.isStatusOK()) {
                logger.error("Erreur de création de codetext. Vo: " + vo.getProperties());
                return new ArrayList<>();
            }
            ids.add(result.getId());
        }
        return ids;
    }

}
