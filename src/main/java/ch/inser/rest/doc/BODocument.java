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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.Tika;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.bo.AbstractBusinessObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.jsl.exceptions.ISException;

import jakarta.ws.rs.core.MultivaluedMap;

/**
 * BO document pour applications avec backend REST
 *
 * @author INSER SA *
 */
public class BODocument extends AbstractBusinessObject {

    /**
     * UID
     */
    private static final long serialVersionUID = -8970898447115190889L;

    /** Définition de la catégorie de logging */
    private static final Log logger = LogFactory.getLog(BODocument.class);

    /** Instance of Tika facade class with default configuration. */
    private final transient Tika iTika = new Tika();

    /**
     * Nom de la propriété qui indique si les documents sont stockés dans des blobs ou dans un file system
     */
    private static final String FILE_DB_PROP = "document.database";
    /**
     * Nom de la propriété qui indique le repetroire temporaire d'upload de document
     */
    private static final String DOCUMENT_UPLOAD_TEMP = "document.upload.temp";
    /**
     * Nom de la propriété qui indique le repetroire d'upload de document
     */
    private static final String DOCUMENT_DIRECTORY = "document.directory";
    /**
     * Champ en base de données du guid du document
     */
    private static final String DOC_FILENAME_GUID = "doc_filename_guid";
    /**
     * Champ en base de données de l'ID du document
     */
    private static final String DOC_OBJ_ID = "doc_obj_id";

    /**
     * Champ en base de données du nom du document
     */
    private static final String DOC_OBJ_NAME = "doc_obj_name";

    /**
     * Champ en base de données du blob du document
     */
    private static final String DOC_BLOB = "doc_blob";

    /**
     *
     * @param aVOInfo
     *            config de Document
     */
    public BODocument(VOInfo aVOInfo) {
        super(ch.inser.dynaplus.util.Constants.Entity.DOCUMENT.toString(), aVOInfo);
    }

    /**
     * Constructeur pour les BO spécialisé de Document
     *
     * @param aEntity
     *            Nom de l'objet Document spécialisé
     * @param aVOInfo
     *            config de l'objet Document spécialisé
     */
    public BODocument(String aEntity, VOInfo aVOInfo) {
        super(aEntity, aVOInfo);
    }

    @Override
    public IDAOResult create(IValueObject valueObject, Connection con, ILoggedUser user) throws SQLException {
        if (!Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
            return createToDirectory(valueObject, con, user);
        }
        return super.create(valueObject, con, user);
    }

    @Override
    public IDAOResult update(IValueObject aValueObject, Connection con, ILoggedUser user) throws SQLException {

        if (!Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
            if (aValueObject.getProperty("doc_old_id") == null) {
                // Création du fichier
                fileWriteToDirectory(aValueObject);
            } else {
                // Déplacement du document dans le nouveau objet métier
                fileCopy(aValueObject);
            }
        }

        // Mise à jour std
        return super.update(aValueObject, con, user);
    }

    @Override
    public IDAOResult delete(Object aId, Timestamp aTimestamp, Connection aConnection, ILoggedUser aUser, DAOParameter... aParameters)
            throws ISException {
        if (!Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
            deleteFileFromDirectory(aId, aConnection, aUser);
        }
        return super.delete(aId, aTimestamp, aConnection, aUser, aParameters);
    }

    @Override
    public Object executeMethode(String aNameMethode, Object anObject, ILoggedUser aUser, Connection aConnection) throws ISException {

        // -- Demande de fonctionnalités
        // Rercherche le path complet du fichier
        if ("getFilePath".equals(aNameMethode)) {
            return getFullFilename((String) ((IValueObject) anObject).getProperty(DOC_FILENAME_GUID), (IValueObject) anObject);
        }
        // Fournir le contenu du fichier (format byte[]
        if ("getDocContents".equals(aNameMethode)) {
            return getDocContents((IValueObject) anObject);
        }
        // Supprimer le fichier à traiter
        if ("deleteFileFromDirectory".equals(aNameMethode)) {
            return deleteFileFromDirectory(anObject, aConnection, aUser);
        }
        // Supprimer tous les fichiers d'un item métier
        if ("cleanDirectory".equals(aNameMethode)) {
            return cleanDirectory((IValueObject) anObject, aUser, aConnection);
        }

        // -- Demande depuis services REST
        if ("restFileUpload".equals(aNameMethode)) {
            return restFileUpload(anObject, aConnection, aUser);
        }
        // Suppression de fichier physique et en BD
        if ("restFileDeleteTemp".equals(aNameMethode)) {
            return restFileDeleteTemp(anObject, aConnection, aUser);
        }
        // Vérifie droit sur enregistrement parent du document
        if ("getVOParent".equals(aNameMethode)) {
            return getVOParent((IValueObject) anObject, aConnection, aUser);
        }

        return super.executeMethode(aNameMethode, anObject, aUser, aConnection);
    }

    /**
     * Rechercher le path complet de fichier à traiter
     *
     * @param aFilename
     *            nom du fichier
     * @param aValueObject
     *            vo Document
     * @return Nom du fichier complet: <docdir>/<objname>/<objId>/<docId>_ <nomFichier>
     */
    protected String getFullFilename(String aFilename, IValueObject aValueObject) {
        StringBuilder fullFileName = new StringBuilder(getContextManager().getProperty(DOCUMENT_DIRECTORY));
        fullFileName.append(File.separator);
        fullFileName.append(aValueObject.getProperty(DOC_OBJ_NAME));
        fullFileName.append(File.separator);
        fullFileName.append(aValueObject.getProperty(DOC_OBJ_ID));
        fullFileName.append(File.separator);
        fullFileName.append(aFilename);
        return fullFileName.toString();
    }

    /**
     * Cherche le contenu du document, soit du blob, soit du fichier du réseau
     *
     * @param aVo
     *            vo document
     * @return le contenu du document en byte[]
     * @throws ISException
     *             erreur de lecture du contenu du fichier
     */
    protected byte[] getDocContents(IValueObject aVo) throws ISException {
        try {
            if (Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
                // Get bytes from blob
                Blob blob = (Blob) aVo.getProperty(DOC_BLOB);
                int blobSize = (int) blob.length();
                return blob.getBytes(1, blobSize);
            }
            // Get bytes from file
            File file = new File(getFullFilename((String) aVo.getProperty(DOC_FILENAME_GUID), aVo));
            return Files.readAllBytes(file.toPath());
        } catch (IOException | SQLException e) {
            throw new ISException(e);
        }
    }

    /**
     * Supprime le fichier physique du directory
     *
     * @param aId
     *            id du document
     * @param aCon
     *            connexion
     * @param aUser
     *            utilisateur
     * @return true si le fichier a été supprimé avec succès
     * @throws ISException
     *             erreur de suppression du fichier (pas vraiment exception SQL)
     */
    protected boolean deleteFileFromDirectory(Object aId, Connection aCon, ILoggedUser aUser) throws ISException {
        // -- Rechercher le record du document dans la base
        IValueObject rec;
        try {
            rec = getRecord(aId, aCon, aUser, false).getValueObject();
        } catch (SQLException e) {
            throw new ISException(e);
        }

        // -- Contrôler si dans fichier temporaire (effacement n'est pas géré
        // ici)
        if ("UPLOAD".equals(rec.getProperty(DOC_OBJ_NAME))) {
            return true;
        }

        String fullFileName = getFullFilename((String) rec.getProperty(DOC_FILENAME_GUID), rec);

        // -- Suppression du fichier
        File file = new File(fullFileName);
        boolean ok;
        try {
            ok = Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new ISException("Erreur de suppression du fichier de directory: " + fullFileName, e);
        }
        if (!ok) {
            throw new ISException("Erreur de suppression du fichier de directory: " + fullFileName);
        }

        // -- Supprimer le dossier si plus de fichiers dans le dossier
        String folderPath = getContextManager().getProperty(DOCUMENT_DIRECTORY) + File.separator + rec.getProperty(DOC_OBJ_NAME)
                + File.separator + rec.getProperty(DOC_OBJ_ID);
        File index = new File(folderPath);
        if (index.exists() && index.list().length == 0) {
            try {
                Files.delete(index.toPath());
            } catch (IOException e) {
                throw new ISException("Erreur de suppression du fichier de directory: " + fullFileName, e);
            }
        }

        return ok;
    }

    /**
     * Supprime tous les fichiers attachés à l'ancienne objet yc le répértoire suite à un update de type "replication"
     *
     * @param aVo
     *            ancien vo objet métier sur lequel des fichiers sont attachés
     * @param aUser
     *            utilisateur
     * @param aConnection
     *            connexion
     * @return true si les fichiers ont été supprimés avec succès
     * @throws ISException
     *             erreur de suppression du directory
     */
    private boolean cleanDirectory(IValueObject aVo, ILoggedUser aUser, Connection aConnection) throws ISException {
        try {
            IValueObject qVo = getVOFactory().getVO(Entity.DOCUMENT);
            qVo.setProperty(DOC_OBJ_NAME, aVo.getVOInfo().getName());
            qVo.setProperty(DOC_OBJ_ID, aVo.getId());
            List<IValueObject> docs = this.getList(qVo, aUser, aConnection).getListObject();
            for (IValueObject doc : docs) {
                IDAOResult result = delete(doc.getId(), doc.getTimestamp(), aConnection, aUser, DAOParameter.EMPTY_PARAMETER);
                if (!result.isStatusOK()) {
                    return false;
                }
            }
            File dir = new File(getContextManager().getProperty(DOCUMENT_DIRECTORY) + File.separator + aVo.getVOInfo().getName()
                    + File.separator + aVo.getId());
            if (!dir.exists()) {
                return true;
            }

            boolean deleted = true;
            for (File file : dir.listFiles()) {
                deleted &= Files.deleteIfExists(file.toPath());
            }
            deleted &= Files.deleteIfExists(dir.toPath());
            return deleted;
        } catch (Exception e) {
            throw new ISException("Erreur de suppression de fichiers pour objet: " + aVo.getId(), e);
        }
    }

    /**
     * Processus d'upload d'un fichier dans la base
     *
     * @param aObject
     *            Fichier et meta-data
     * @param aConnection
     *            connexion
     * @param aUser
     *            utilisateur
     * @return id de la table t_document <code>-1</code> Erreur générique <code>-2</code> taille de fichier trop grand <code>-3</code> Nom
     *         de fichier pas autorisé
     * @throws ISException
     *             erreur 'décriture dans la base de données ou directory
     */
    private Object restFileUpload(Object aObject, Connection aConnection, ILoggedUser aUser) throws ISException {

        Object id = -1l;

        // Recherche le fichier et les informations du fichier
        Map<String, List<InputPart>> uploadForm = ((MultipartFormDataInput) aObject).getFormDataMap();
        List<InputPart> inputParts = uploadForm.get("uploadFile");

        // Si aucun fichier est présent retourne -1
        if (inputParts == null) {
            logger.warn("No file content");
            return id;
        }

        // -- Contrôler le type du document accepté
        InputPart inputPart = inputParts.get(0);
        MultivaluedMap<String, String> header = inputPart.getHeaders();
        String filename = fileReadFileName(header);
        logger.debug("Filename : " + filename);
        String type = header.getFirst("Content-Type");
        String extension = getFileExtension(filename);

        List<String> lstMimeTypeAccepted = getMimeTypeAccepted(extension);

        if ("unknown".equals(filename) || lstMimeTypeAccepted.isEmpty()) {
            return -3l;
        }

        try {
            // -- Sauvegarder le fichier dans le dossier upload
            InputStream inputStream = inputPart.getBody(InputStream.class, null);
            byte[] bytes = IOUtils.toByteArray(inputStream);

            // Vérification de la taille du fichier
            if (bytes.length < Integer.parseInt(getContextManager().getProperty("document.maxfilesize"))) {

                // -- Check MIME-TYPE
                if (checkMimeType(type, lstMimeTypeAccepted, bytes)) {

                    IValueObject vo = getInitVO(aUser, aConnection, false);
                    // -- Ecrire fichier temporaire
                    if (!Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
                        // -- Créer un GUID pour le nom de fichier
                        UUID uuid = UUID.randomUUID();
                        String filenameGuid = uuid.toString();
                        fileWriteInTemp(bytes, filenameGuid);
                        vo.setProperty(DOC_FILENAME_GUID, filenameGuid);
                    } else {
                        vo.setProperty(DOC_BLOB, new javax.sql.rowset.serial.SerialBlob(bytes));
                    }

                    // -- Créer un item dans table t_document
                    vo.setProperty(DOC_OBJ_NAME, "UPLOAD");
                    vo.setProperty(DOC_OBJ_ID, -1l);
                    vo.setProperty("doc_filename", filename);
                    vo.setProperty("doc_mimetype", type);

                    id = create(vo, aConnection, aUser).getId();

                    logger.debug("Fichier sauvegarder :" + vo.getId() + ", filename : " + vo.getProperty("doc_filename"));
                } else {
                    id = -3l;
                }

            } else {
                logger.warn("Taille du fichier dépassé : " + bytes.length + ", filename : " + filename);
                id = -2l;
            }

            // Fermeture du stream
            inputStream.close();

        } catch (SQLException | IOException e) {
            throw new ISException(e);
        }

        return id;
    }

    /**
     * Suppression d'un fichier temporaire depuis le service REST, uniquement suppression dans le dossier de fichier temporaire.
     *
     * Le doc_obj_name="UPLOAD" sera pris en considération pas les autres, cette fonction est utilisé lorsque l'utilisateur n'a pas encore
     * sauvegarder l'item de l'bojet métier
     *
     * @param aAnObject
     *            <code>IValueObject</code> Objet document à supprimer
     * @param aConnection
     *            connexion
     * @param aUser
     *            Utilisateur
     * @return Le nombre de records impliqués par la mise à jour
     * @throws ISException
     *             Erreur de suppression du fichier dans table t_document
     */
    private Object restFileDeleteTemp(Object aAnObject, Connection aConnection, ILoggedUser aUser) throws ISException {
        IValueObject voDoc = (IValueObject) aAnObject;
        if (!Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
            String filename = getContextManager().getProperty(DOCUMENT_UPLOAD_TEMP) + File.separator + voDoc.getProperty(DOC_FILENAME_GUID);

            // -- Suppression du fichier
            File file = new File(filename);
            boolean ok;
            try {
                ok = Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                throw new ISException("Erreur de suppression du fichier dans dossier temp: " + filename, e);
            }
            if (!ok) {
                throw new ISException("Erreur de suppression du fichier dans dossier temp: " + filename);
            }
        }

        return delete(voDoc.getId(), voDoc.getTimestamp(), aConnection, aUser, DAOParameter.EMPTY_PARAMETER).getNbrRecords();

    }

    /**
     *
     * @param aValueObject
     *            vo Document
     * @param aCon
     *            connexion
     * @param aUser
     *            utilisateur
     * @return id
     * @throws SQLException
     *             problème au nivean db
     */
    private IDAOResult createToDirectory(IValueObject aValueObject, Connection aCon, ILoggedUser aUser) throws SQLException {
        // Remove blob from vo
        aValueObject.removeProperty(DOC_BLOB);
        IValueObject voBackup = (IValueObject) aValueObject.clone();

        // Create record first to generate doc_id
        IDAOResult result = super.create(aValueObject, aCon, aUser);
        voBackup.setId(result.getId());

        return result;
    }

    /**
     * Ecrit un fichier dans un répértoire
     *
     * @param aValueObject
     *            vo Document
     * @throws SQLException
     *             Erreur de localisation ou d'écriture du fichier
     */
    protected void fileWriteToDirectory(IValueObject aValueObject) throws SQLException {

        String fullFileName = getFullFilename((String) aValueObject.getProperty(DOC_FILENAME_GUID), aValueObject);

        File blobFile = new File(fullFileName);
        if (!blobFile.exists()) {
            // Create the directories specified in the filename path
            File tmp = new File(fullFileName.substring(0, fullFileName.lastIndexOf(File.separator)));
            tmp.mkdirs();
        }

        try (FileOutputStream outStream = new FileOutputStream(blobFile)) {
            // -- Mettre le fichier dans dossier document
            byte[] fileContents = (byte[]) aValueObject.getProperty("doc_bytes");
            outStream.write(fileContents);
            outStream.flush();

            // -- Supprimer fichier temporaire
            String fileTempDir = getContextManager().getProperty(DOCUMENT_UPLOAD_TEMP) + File.separator
                    + aValueObject.getProperty(DOC_FILENAME_GUID);
            File fileTmp = new File(fileTempDir);
            Files.deleteIfExists(fileTmp.toPath());

        } catch (IOException e) {
            logger.error("Erreur de localisation ou d'écriture du fichier: " + fullFileName, e);
            throw new SQLException(e);
        }

    }

    /**
     * Copie un fichier dans un nouveau répértoire quand le doc_obj_id change à cause d'un update de type "replication"
     *
     * Location: <docdir>/<objname>/<objId>/<docId_nomFichier>
     *
     * @param aValueObject
     *            vo Document
     * @throws SQLException
     *             Erreur de copie de fichier
     *
     *
     */
    protected void fileCopy(IValueObject aValueObject) throws SQLException {
        IValueObject oldVo = (IValueObject) aValueObject.clone();
        oldVo.setProperty(DOC_OBJ_ID, aValueObject.getProperty("doc_old_id"));
        String oldLocation = getFullFilename((String) aValueObject.getProperty(DOC_FILENAME_GUID), oldVo);
        File file = new File(oldLocation);
        if (!file.exists()) {
            throw new SQLException("Fichier origine plus disponible :" + oldLocation);
        }
        String newLocation = getFullFilename((String) aValueObject.getProperty(DOC_FILENAME_GUID), aValueObject);
        String newDir = newLocation.substring(0, newLocation.lastIndexOf(File.separator));
        File dir = new File(newDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Attempt to copy the file
        try {
            Files.copy(Paths.get(oldLocation), Paths.get(newLocation));
        } catch (IOException e) {
            logger.error("Erreur copie de fichier", e);
            throw new SQLException(e);
        }
    }

    /**
     * Fournir nom de l'extension à l'aide du nom de fichier
     *
     * @param aFilename
     *            nom fichier
     * @return extension
     */
    private String getFileExtension(String aFilename) {
        String extension = "";

        // -- Recherche du nom de l'extension
        int i = aFilename.lastIndexOf('.');
        if (i > 0) {
            extension = aFilename.substring(i + 1);
        }

        return extension;
    }

    /**
     * Fournir la liste des MIME-TYPE autorisé selon l'extension
     *
     * @param aExtension
     *            extension du fichier
     * @return Liste des MIME-TYPE
     */
    private List<String> getMimeTypeAccepted(String aExtension) {
        List<String> lstMimeType = new ArrayList<>();

        String[] extensionsProperty = getContextManager().getProperty("document.mimetype").split(";");

        for (String extension : extensionsProperty) {
            String[] mimeArray = extension.split(",");

            if (mimeArray[0].equalsIgnoreCase(aExtension)) {
                // Copie les éléments du tableau de mime sauf le premier qui donne l'extension
                Collections.addAll(lstMimeType, Arrays.copyOfRange(mimeArray, 1, mimeArray.length));
            }
        }
        if (lstMimeType.isEmpty()) {
            logger.warn("No accepted mime type found for extension: " + aExtension);
        }

        return lstMimeType;

    }

    /**
     * Contrôler le Mime-type en relation avec l'extension du fichier
     *
     * @param aHttpType
     *            vrai nom du fichier
     * @param aLstMimeType
     *            extension du fichier
     * @param aBytes
     *            contenu du fichier
     * @return true si MIME-TYPE ok pour l'extension
     */
    private boolean checkMimeType(String aHttpType, List<String> aLstMimeType, byte[] aBytes) {
        // Type provenant de la requête HTTP
        logger.debug("Request HTTP MIME-TYPE : " + aHttpType);

        // -- Rechercher MIME-TYPE selon contenu du fichier
        String typeAnalyse = iTika.detect(aBytes);
        logger.debug("Tika analyse MIME-TYPE : " + typeAnalyse);

        // -- Comparaison avec MIME autorisé selon nom de l'extension et du type
        // selon requête HTTP
        if (aLstMimeType.contains(typeAnalyse) && aLstMimeType.contains(aHttpType)) {
            return true;
        }

        logger.warn("MIME-TYPE not accepted: " + typeAnalyse + ", http type: " + aHttpType);

        return false;
    }

    /**
     * Save file in temporary upload directory for future use
     *
     * @param content
     *            file content
     * @param aFilename
     *            file name (filename is a GUID)
     * @throws IOException
     *             error writing file to directory
     */
    private void fileWriteInTemp(byte[] content, String aFilename) throws IOException {
        // Path dans dossier temp
        String filename = getContextManager().getProperty(DOCUMENT_UPLOAD_TEMP) + File.separator + aFilename;

        // Ecrire le fichier
        File file = new File(filename);
        if (!file.createNewFile()) {
            logger.info("Replace the file: " + file);
        }

        try (FileOutputStream fop = new FileOutputStream(file)) {
            fop.write(content);
            fop.flush();
        }
    }

    /**
     * Cherche le nom du fichier dans le header de l'élément de upload header
     *
     * Sample { Content-Type=[image/png], Content-Disposition=[form-data; name="file"; filename="filename.extension"] }
     *
     * @param header
     *            Le header du fichier
     * @return nom du fichier ou "unknown"
     **/
    protected String fileReadFileName(MultivaluedMap<String, String> header) {

        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

        for (String filename : contentDisposition) {
            if (filename.trim().startsWith("filename")) {

                String[] nameComplete = filename.split("=");

                // Trim les espaces
                String name = nameComplete[1].trim().replace("\"", "");

                if (StandardCharsets.US_ASCII.newEncoder().canEncode(name)) {
                    // Test du 1er caractère
                    Pattern pattern = Pattern.compile(getContextManager().getProperty("document.checkfilename"),
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    Matcher matcher = pattern.matcher(name);

                    // Si accepté
                    if (matcher.matches()) {
                        // Modifie les caractères non désirés
                        return name.replaceAll("[\\*/\\\\!\\|:?<>]", "");
                    }
                }
                logger.warn("Filename not accepted: " + name);
            }
        }
        return "unknown";
    }

    @Override
    public IDAOResult getRecord(Object aId, Connection aCon, ILoggedUser aUser, boolean aGetParent, DAOParameter... aParameters)
            throws SQLException {
        IDAOResult result = super.getRecord(aId, aCon, aUser, aGetParent, aParameters);
        IValueObject rec = result.getValueObject();
        if (rec == null) {
            return result;
        }

        if (result.getValueObject() != null && Boolean.parseBoolean(getContextManager().getProperty(FILE_DB_PROP))) {
            try {
                /*
                 * On recupère le contenu du BLOB et on va la mettre dans un autre champ temporaire On est obligé de faire ça car si on
                 * recupère le BLOB en dehors d'un connection on se choppe une erreur de type "Connexion interrompue"
                 */
                byte[] blobByteContent;
                blobByteContent = getDocContents(result.getValueObject());
                result.getValueObject().setProperty("doc_blob_byte", blobByteContent);
            } catch (ISException e) {
                logger.warn("Erreur getRecord", e);
            }
        }
        return result;
    }

    /**
     * Recupère le vo de l'enregistrement parent sur lequel le document est lié
     *
     * @param aVo
     *            vo document
     * @param aCon
     *            connexion
     * @param aUser
     *            utilisateur
     * @return vo parent
     * @throws ISException
     *             erreur de consultation de l'objet parent
     */
    protected IValueObject getVOParent(IValueObject aVo, Connection aCon, ILoggedUser aUser) throws ISException {
        if (aVo.getProperty(DOC_OBJ_NAME) == null || aVo.getProperty(DOC_OBJ_ID) == null) {
            throw new UnsupportedOperationException("Les champs pour chercher l'objet parent manquent. Il faut spécialiser cette méthode");
        }
        try {
            return getBOFactory().getBO((String) aVo.getProperty(DOC_OBJ_NAME)).getRecord(aVo.getProperty(DOC_OBJ_ID), aCon, aUser, false)
                    .getValueObject();
        } catch (SQLException e) {
            throw new ISException(e);
        }
    }

}
