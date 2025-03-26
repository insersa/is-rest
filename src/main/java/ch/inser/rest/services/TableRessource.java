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

package ch.inser.rest.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

/**
 * Permet de transformer un tableau en un format de fichier désiré (par exemple Excel)
 *
 * @author INSER SA *
 */
@Path("/table")
@Api(value = "table")
public class TableRessource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(TableRessource.class);

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * Le tableau intermédiaire pour le nom des champs
     */
    private ArrayList<String> iFieldList = new ArrayList<>();

    /**
     * Le tableau intermédiaire pour le type des champs
     */
    private ArrayList<String> iFieldType = new ArrayList<>();

    /**
     * Le tableau intermédiaire pour le domain des champs
     */
    private ArrayList<String> iFieldDomain = new ArrayList<>();

    /**
     * Export un tableau vers le format voulu (p. ex Excel)
     *
     * @param aToken
     *            Token de sécurité avec userId, userName etc. fourni comme paramètre de header
     * @param jsonRequest
     *            La requete JSON avec les paramètres suivants :<br>
     *            - filename : Nom du fichier de sortie<br>
     *            - extension : Extension du fichier<br>
     *            - columns : Les colonnes de la table sous forme json. Chaque objet doit avoir comme attribut :<br>
     *            ==> un champ field représantant le nom en BD<br>
     *            ==> un champ name représant le nom de la colonne<br>
     *            ==> un champ type pour savoir le type de champ sous forme de JSON (Exemple : {field: "pre_operational_center", name: "OC",
     *            type: "string", domain: "test" }). Les champs field, name et type sont obligatoires, domain peut être null.<br>
     *            - content : Le contenu de la table sous forme de json. Le contenu doit correspondre au "field" donné<br>
     *            - contentAsQuery : si la valeur est true, cela veut dire que le contenu contient une requête qui doit être executée -
     *            domains : le contenu des coded value domains sous forme de JSON
     * @return le fichier dans le format voulu
     */
    @ApiOperation(value = "Export a table")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 500, message = "Error querying ") })
    @POST
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response exportTable(@ApiParam(value = "Security token in the header", required = false) @HeaderParam("token") String aToken,
            String jsonRequest) {

        try {
            // Check the security
            Claims claims = RestUtil.getClaims(aToken);
            // -- Contrôle si paramètre présent
            if (claims == null || "".equals(jsonRequest) || jsonRequest == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Recupère les paramètres de la requête
            JsonObject params = JsonUtil.stringToJsonObject(jsonRequest);
            final String filename = params.getString("filename");
            final String extension = params.getString("extension");
            final String columns = params.getString("columns");
            final String domains = params.getString("domains");
            final boolean contentAsQuery = params.getBoolean("contentAsQuery");
            String content = params.getString("content");
            logger.debug(String.format("TableRessource.exportTable, filename='%s' extension='%s'", filename, extension));

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            if (contentAsQuery) {
                content = parseContent(content);
            }

            byte[] contents = null;
            // Création du fichier excel
            if ("xlsx".equals(extension)) {
                contents = createExcel(columns, content, contentAsQuery, domains);
            } else {
                // Format non reconnu
                logger.error("Extension not recognized: " + extension);
                return Response.status(Status.BAD_REQUEST).build();
            }

            if (contents == null) {
                logger.error("Erreur content is null");
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }

            // Réponse JSON
            ResponseBuilder response = null;
            response = Response.ok(new ByteArrayInputStream(contents));
            response.header("Content-Disposition", "attachment;filename=\"" + filename + "." + extension + "\"");
            response.type(getContentType(extension));
            response.encoding("UTF-8");
            return response.build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Login error", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Error exportTable", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

    }

    /**
     * Crée un fichier Excel (format .xslx) avec les données passées en paramètres
     *
     * @param aColumns
     *            Les colonnes de la table sous forme json. Chaque objet doit avoir comme attribut :<br>
     *            - un champ field représantant le nom en BD<br>
     *            - un champ name représant le nom de la colonne<br>
     *            - un champ type pour savoir le type de champ<br>
     *            - un champ domain pour savoir si le type de champ est un coded value domain<br>
     *            - Les champs field, name et type sont obligatoires, domain peut être null.<br>
     * @param aContent
     *            Le contenu de la table sous forme de json. La taille doit être identique à celle des colonnes
     * @param aContentAsQuery
     *            Si la valeur est true, cela veut dire que le contenu contient une requête qui doit être executée
     * @param aDomains
     *            Le contenu des coded value domains sous forme de JSON
     *
     * @return le fichier sous forme de tableau d'octets
     *
     */
    private byte[] createExcel(String aColumns, String aContent, boolean aContentAsQuery, String aDomains) {
        logger.debug(
                String.format("TableRessource.createExcel, aColumns size='%s', content size='%s', contentAsQuery='%s', domains size='%s'",
                        aColumns.length(), aContent.length(), aContentAsQuery, aDomains.length()));
        byte[] contents = null;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();

            // Gestion des lignes, reset des tables intermediaires
            int indexRow = 0;
            iFieldList = new ArrayList<>();
            iFieldType = new ArrayList<>();
            iFieldDomain = new ArrayList<>();

            // Convertit les domain en JSON
            JsonObject domains = JsonUtil.stringToJsonObject(aDomains);

            // Crée l'en-tete du tableau
            try (JsonReader jsonReader = Json.createReader(new StringReader(aColumns));) {
                JsonArray array = jsonReader.readArray();
                Row row = sheet.createRow(indexRow++);
                formatHeaders(array, row);
            }

            // Ecrit le contenu du tableau dans le fichier Excel
            try (JsonReader jsonReader = Json.createReader(new StringReader(aContent))) {
                JsonArray array = jsonReader.readArray();
                for (JsonValue val : array) {
                    Row row = sheet.createRow(indexRow++);
                    // Hashmap champ valeur
                    HashMap<String, Object> attributesMap = new HashMap<>();
                    attributesMap = getAttributesMap(aContentAsQuery, val);
                    formatAttributes(domains, row, attributesMap);
                }
            }

            // Création du fichier sous forme de bytes array
            ByteArrayOutputStream outByteStream = new ByteArrayOutputStream();
            workbook.write(outByteStream);
            contents = outByteStream.toByteArray();
            outByteStream.close();
            logger.debug("TableRessource.createExcel, created Excel file");
        } catch (IOException e) {
            // -- Erreur
            logger.error("Error createExcel", e);
        }
        return contents;
    }

    /**
     * Retourne le content-type suivant l'extesion de fichier
     *
     * @param aExtension
     *            L'extension de fichier
     * @return le content-type
     */
    protected String getContentType(String aExtension) {
        if ("xlsx".equals(aExtension)) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "";
    }

    /**
     * Formate les attributs de la table dans le fichier Excel
     *
     * @param aArray
     *            la liste des headers
     * @param aRow
     *            La ligne en cours dans le fichier Excel
     */
    @SuppressWarnings("unchecked")
    protected void formatHeaders(JsonArray aArray, Row aRow) {
        try {
            logger.debug("TableRessource.formatHeaders");
            int indexCol = 0;
            for (JsonValue val : aArray) {
                HashMap<String, String> headersMap = (HashMap<String, String>) JsonUtil.jsonValueToObject(val);
                aRow.createCell(indexCol).setCellValue(headersMap.get("name"));
                iFieldList.add(headersMap.get("field"));
                iFieldType.add(headersMap.get("type"));
                iFieldDomain.add(headersMap.get("domain"));
                indexCol++;
            }

        } catch (Exception e) {
            logger.error("Error formatHeaders", e);
        }
    }

    /**
     * Formate les attributs de la table dans le fichier Excel
     *
     * @param aDomains
     *            La liste des coded value domain
     * @param aRow
     *            La ligne en cours dans le fichier Excel
     * @param aAttributesMap
     *            La map clé/valeur des attributs
     */
    protected void formatAttributes(JsonObject aDomains, Row aRow, HashMap<String, Object> aAttributesMap) {
        try {
            int indexCol = 0;
            // On va prendre dans l'ordre du tableau les champs pour mettre les
            // valeurs de la Hasmap créé en dessus
            for (int i = 0; i < iFieldList.size(); i++) {
                Object cellVal = aAttributesMap.get(iFieldList.get(i));
                if (cellVal != null) {
                    // Effectue le formatage selon la valeur du champ
                    if ("date".equals(iFieldType.get(i))) {
                        Date date = new Date(Long.parseLong(cellVal.toString()));
                        String dateString = new SimpleDateFormat("dd/MM/yyyy").format(date);
                        aRow.createCell(indexCol++).setCellValue(dateString);
                    } else {
                        if (iFieldDomain.get(i) != null) {
                            aRow.createCell(indexCol++).setCellValue(getDomainValue(iFieldDomain.get(i), aDomains, cellVal.toString()));
                        } else {
                            aRow.createCell(indexCol++).setCellValue(cellVal.toString());
                        }
                    }
                } else {
                    aRow.createCell(indexCol++).setCellValue("");
                }
            }
        } catch (Exception e) {
            logger.error("Error formatAttributes", e);
        }
    }

    /**
     * Recherche dans les domaines la clé correspondant
     *
     * @param aFieldDomain
     *            le nom du domain
     * @param aDomains
     *            Les domaines
     * @param aCellVal
     *            clé de la valeur
     * @return la valeur du coded value domain ou la clé si pas trouvé
     */
    protected String getDomainValue(String aFieldDomain, JsonObject aDomains, String aCellVal) {
        if (aDomains != null && aDomains.getJsonObject(aFieldDomain) != null && !"".equals(aCellVal)) {
            return aDomains.getJsonObject(aFieldDomain).getString(aCellVal);
        }
        return aCellVal;
    }

    /**
     * Retourne la liste des attributs sous forme de HashMap
     *
     * @param aContentAsQuery
     *            true si le contenu était une requête
     * @param aAttributes
     *            les attributs
     * @return une HashMap clé/valeur des attributs
     */
    @SuppressWarnings("unchecked")
    protected HashMap<String, Object> getAttributesMap(boolean aContentAsQuery, JsonValue aAttributes) {
        try {
            // Si le contenu a été créé a partir d'une query, on
            // va chercher dans l'objet "attributes"
            if (aContentAsQuery) {
                JsonObject attributes = ((JsonObject) aAttributes).getJsonObject("attributes");
                return (HashMap<String, Object>) JsonUtil.jsonObjectToMap(attributes);
            }
            return (HashMap<String, Object>) JsonUtil.jsonValueToObject(aAttributes);
        } catch (Exception e) {
            logger.error("Error getAttributesMap", e);
        }
        return null;
    }

    /**
     * Execute the query to create the content. <br>
     * This method must be implemented in your project if you want to use it
     *
     * @param aContent
     *            the content as a query
     * @return the content as a stringified JSON
     */
    protected String parseContent(String aContent) {
        return aContent;
    }

    /**
     * @return la liste des champs
     */
    public List<String> getFieldList() {
        return iFieldList;
    }

    /**
     * @return le liste du type du champ
     */
    public List<String> getFieldType() {
        return iFieldType;
    }

    /**
     * @return la list du domain du champ
     */
    public List<String> getFieldDomain() {
        return iFieldDomain;
    }

}
