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

package ch.inser.rest.util;

/**
 * Constants pour la librairie is-rest
 *
 * @author INSER SA *
 */
public class Constants {

    /**
     * Private constructor for class with static methods
     */
    private Constants() {
        // DO nothing
    }

    /** Attribute name service locator */
    public static final String SERVICE_LOCATOR = "ServiceLocator";

    /** Attribute name token */
    public static final String TOKEN = "token";

    /** Attribute name record */
    public static final String RECORD = "record";

    /**
     * @author INSER SA     *
     */
    public enum Entity {
        /**
         * L'objet formulaire
         */
        FORMULARS,
        /**
         * Le body du formulaire
         */
        BODY,
        /**
         * Le type, s'il existe comme objet métier dans le config
         */
        TYPE,

        /**
         * Utilisateur
         */
        USER,

        /** Commune */
        COMMUNE,

        /** Rôle */
        USERGROUP,

        /**
         * Object métier Logged user pour gérer le login et logout dans le token
         * resource
         */
        LOGGED_USER;

        @Override
        public String toString() {
            if (this == USER) {
                return "User";
            }
            if (this == COMMUNE) {
                return "Commune";
            }
            if (this == USERGROUP) {
                return "UserGroup";
            }
            if (this == LOGGED_USER) {
                return "LoggedUser";
            }
            return super.toString();
        }
    }

    /**
     * Résultat dé schéduling d'un job asynchrone
     *
     * @author INSER SA     *
     */
    public enum ProcedureResult {

        /**
         * Procédure démarrée
         */
        STARTED,
        /**
         * Une erreur c'est produite
         */
        ERROR,
        /**
         * La procédure est en cours d'exécution et n'est pas démarrées une
         * deuxième fois
         */
        ALREADY_RUNNING,
        /**
         * La procédure n'est pas démarrée
         */
        NOT_STARTED;

    }

    /**
     * Les éléments d'une instruction json patch
     *
     * Spécialité IS: date pour valider le timestamp
     *
     * @author INSER SA     *
     */
    public enum PatchItem {
        /** Opération */
        OP,
        /** Chemin de l'objet à modifier, p.ex. nom du champ */
        PATH,
        /** Nouvelle valeur */
        VALUE,
        /** Timestamp */
        DATE;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    /**
     * Les operations d'une instruction json patch
     *
     * @author INSER SA     *
     */
    public enum PatchOperation {
        /** Remplacer */
        REPLACE,
        /** Ajouter */
        ADD,
        /** Supprimer */
        REMOVE,
        /** Tester */
        TEST;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    /**
     * Verbe pour la sécurité à utiliser pour les services REST
     *
     * @author INSER SA     *
     */
    public enum Verb {
        /** Création d'un item */
        POST,

        /** Réception item ou list d'item */
        GET,

        /** Modifier tout l'item */
        PUT,

        /** Modification d'une partie de l'item */
        PATCH,

        /** Suppression **/
        DELETE;

    }

    /**
     * Formats de données données en retour par les web services
     *
     * @author INSER SA     *
     */
    public enum DataFormat {
        /** Format PDF (généré par Birt) */
        PDF,
        /** Format Json (transformé depuis vo(s)) */
        JSON;

        /**
         *
         * @param aFormatStr
         *            nom du format, ex. "pdf", "json"
         * @return format
         */
        public static DataFormat parse(String aFormatStr) {
            switch (aFormatStr.toUpperCase()) {
                case "PDF":
                    return PDF;
                case "JSON":
                    return JSON;
                default:
                    return null;
            }
        }
    }
}
