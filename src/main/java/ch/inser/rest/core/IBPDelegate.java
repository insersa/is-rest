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

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Mode;
import ch.inser.dynaplus.util.IService;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.list.ListHandler.Sort;

/**
 * BP delegate pour is-rest
 *
 * @author INSER SA *
 */
public interface IBPDelegate extends IService, Serializable {

    /**
     * Lit un enregistrement correspondant à l'ID spécifié.
     *
     * @param id
     *            Identifiant
     * @param user
     *            Utilisateur
     * @param aParameters
     *            paramètres pour la récuperation de l'enregistrement.
     * @return Un value object contenant l'enregistrement demandé.
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult getRecord(Object id, ILoggedUser user, DAOParameter... aParameters) throws ISException;

    /**
     * Met à jour un enregistrement. D'abord, le value object passé en paramètre doit être comparé avec celui de référence dans cet objet
     * métier afin de déterminer les attributs qui ont été modifiés. Une collection de ces attributs doit être constituée et passée à la
     * méthode de mise à jour dans le DAO correspondant.
     *
     * @param valueObject
     *            Value object contenant l'enregistrement modifié par l'utilisateur.
     * @param user
     *            utilisateur
     * @return Le nombre de records impliqués par la mise à jour si positif, sinon:<br>
     *         0 : aucun record mis à jour <br>
     *         -1 : pas trouvé le record à modifier <br>
     *         -2 : le record a changé entre temps <br>
     *         -3 : rien n'a été modifié par le user, pas de update (court-circuit)<br>
     *         -4 : les droits d'accès n'autorisent pas cette modif
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult update(IValueObject valueObject, ILoggedUser user) throws ISException;

    /**
     * Update and delete the records using the business process.
     *
     * @param aRecords
     *            the records to add or/and update if necessary
     * @param aDeletes
     *            the records to delete
     * @param aLoggedUser
     *            the logged user
     * @param aParameter
     *            the parameters
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    public IDAOResult update(List<IValueObject> aRecords, List<IValueObject> aDeletes, ILoggedUser aLoggedUser, DAOParameter... aParameter)
            throws ISException;

    /**
     * Crée un nouvel enregistrement.
     *
     * @param valueObject
     *            Value object contenant l'enregistrement à créer.
     * @param user
     *            utilisateur
     * @return Le ID du record nouvellement créé
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult create(IValueObject valueObject, ILoggedUser user) throws ISException;

    /**
     * Supprime un enregistrement.
     *
     * @param id
     *            L'identifiant de l'objet à supprimer.
     * @param timestamp
     *            Timestamp de l'objet d'origine
     * @param user
     *            Utilisateur
     * @param aParameter
     *            Les paramêtres
     * @return Le resultat de l'effacement
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données
     */
    public IDAOResult delete(Object id, Timestamp timestamp, ILoggedUser user, DAOParameter... aParameter) throws ISException;

    /**
     * Retourne le timestamp
     *
     * @param id
     *            Identifiant
     * @param user
     *            Utilisateur
     * @return Le timestamp de l'objet
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult getTimestamp(Object id, ILoggedUser user) throws ISException;

    /**
     * Retourne la valeur d'un champ pour l'enregistrement donné
     *
     * @param id
     *            id d'enregistrement
     * @param fieldName
     *            nom du champ
     * @return valeur du champ
     * @throws ISException
     *             erreur au niveau bd
     */
    public IDAOResult getField(Object id, String fieldName) throws ISException;

    /**
     * Obtenition de la liste des valeurs d'un champ sur la base de la requête fournie
     *
     * @param aVo
     *            restriction pour la mise à jour (uniquement les champs de la table principale)
     * @param aFieldName
     *            le nom du champ à modifier
     * @param aParameters
     *            paramètres (optionnel)
     * @return la liste des valeurs
     * @throws ISException
     *             erreur requête bd
     */
    public IDAOResult getFieldsRequest(IValueObject aVo, String aFieldName, DAOParameter... aParameters) throws ISException;

    /**
     * Obtenition de la liste des valeurs d'un champ sur la base de la requête fournie
     *
     * @param aVo
     *            restriction pour la mise à jour (uniquement les champs de la table principale)
     * @param aFieldName
     *            le nom du champ à modifier
     * @param aUser
     *            utilisateur
     * @param aParameters
     *            paramètres (optionnel)
     * @return la liste des valeurs
     * @throws ISException
     *             erreur requête bd
     */
    public IDAOResult getFieldsRequest(IValueObject aVo, String aFieldName, ILoggedUser aUser, DAOParameter... aParameters)
            throws ISException;

    // ----------------------------------------- Méthodes d'initialisation de VO
    /**
     * Méthode retournant un VO avec valeures initiales
     *
     * @param user
     *            utilisateur
     * @return VO avec valeures initiales
     * @throws ISException
     *             erreur d'initialisation
     */
    public IValueObject getInitVO(ILoggedUser user) throws ISException;

    /**
     * @param mode
     *            mode
     * @param user
     *            utilisateur
     * @return init vo
     * @throws ISException
     *             erreur d'init
     */
    public IValueObject getInitVO(Mode mode, ILoggedUser user) throws ISException;

    /**
     * Traite une requête de recherche et fournit une liste d'entités sous forme d'une collection.
     *
     * @param aVo
     *            Value object contenant les critères de recherche
     * @param aUser
     *            Utilisateur
     * @param aParameters
     *            Les paramêtres
     * @return Le resultat de la recherche
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult getList(IValueObject aVo, ILoggedUser aUser, DAOParameter... aParameters) throws ISException;

    /**
     * Requête de count
     *
     * @param vo
     *            Value object contenant les critères de recherche
     * @param aUser
     *            utilisateur (optionnel)
     * @param aParameters
     *            paramètres (optionnel)
     * @return nbr d'enregistrement
     * @throws ISException
     *             erreur de requête bd
     */
    public IDAOResult getListCount(IValueObject vo, ILoggedUser aUser, DAOParameter... aParameters) throws ISException;

    /**
     * Permet de modifier un champ sur chaque ligne d'une liste, la valeur à inserér pour le changement se trouve dans aLstValue.
     *
     * @param aLstIds
     *            list des ids à modifier
     * @param aFieldName
     *            nom du champ à modifier dans la db
     * @param aLstValues
     *            valeur à modifier selon la liste d'id
     * @param aUser
     *            paramètre de l'utilisateur
     * @return Le nombre de records impliqués par la mise à jour si positif, sinon:<br>
     *         0 : aucun record mis à jour <br>
     *         -1 : pas trouvé le record à modifier <br>
     *         -2 : le record a changé entre temps <br>
     *         -3 : rien n'a été modifié par le user, pas de update (court-circuit)<br>
     *         -4 : les droits d'accès n'autorisent pas cette modif
     * @throws ISException
     *             en cas de problème au niveau de la requête à la base de données.
     */
    public IDAOResult updateField(List<Object> aLstIds, String aFieldName, List<Object> aLstValues, ILoggedUser aUser) throws ISException;

    /**
     * Retourne la clé du tri par défaut
     *
     * @return clé de tri par défaut
     */
    public String getDefaultOrderKey();

    /**
     *
     * @return Retourne l'orientation du tri par défaut
     */
    public Sort getDefaultSortOrder();

    /**
     * Actualise les valeurs de plusieurs champs pour l'enregistrement donné
     *
     * @param id
     *            identifiant de l'objet
     * @param aFieldNames
     *            noms des champs à modifier
     * @param aValues
     *            valeurs à inserer
     * @return nbr de records modifiés
     * @throws ISException
     *             si un problème survient au niveau base de données
     */
    public IDAOResult updateFields(Object id, String[] aFieldNames, Object[] aValues) throws ISException;

    /**
     * Update some values from a value object.
     *
     * @param aValueObject
     *            the value object, must contain the object ID and timestamp to find and check the object to update, if a field value is
     *            <code>null</code> it's unchanged, if it's <code>Operator.IS_NULL</code> it's removed
     * @param aUser
     *            the user
     * @return the full updated <code>IValueObject</code>
     * @throws ISException
     *             is a problem was found
     */
    public IDAOResult updateFields(IValueObject aValueObject, ILoggedUser aUser) throws ISException;

    /**
     *
     * @param aNameMethode
     *            nom de méthode à éxecuter
     * @param anObject
     *            objet fourni
     * @param aUser
     *            utilisateur
     * @return résultat de l'éxecution
     * @throws ISException
     *             erreur d'execution
     */
    public Object executeMethode(String aNameMethode, Object anObject, ILoggedUser aUser) throws ISException;

}
