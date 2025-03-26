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

package ch.inser.rest.doc.procedure;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.auth.SuperUser;
import ch.inser.dynaplus.bo.BOFactory;
import ch.inser.dynaplus.quartz.IJobInjection;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.vo.IVOFactory;
import ch.inser.jsl.exceptions.ISException;

/**
 * Vide le répértoire temporaire de upload
 *
 * @author INSER SA *
 */
public class CleanUploadDirJob implements org.quartz.InterruptableJob {

    /** BOFactory */
    private BOFactory iBOFactory;

    /** VOFactory */
    private IVOFactory iVOFactory;

    /** Context manager */
    private IContextManager iCtx;

    /** Utilisateur qui execute l'épuration */
    private ILoggedUser iUser;

    /**
     * Définition du Logger utilisé pour le logging.
     */
    private static final Log logger = LogFactory.getLog(CleanUploadDirJob.class);

    @Override
    public void execute(JobExecutionContext aContext) throws JobExecutionException {

        Map<?, ?> dataMap = aContext.getJobDetail().getJobDataMap();
        IJobInjection jobInjection = (IJobInjection) dataMap.get("jobInjection");
        iBOFactory = jobInjection.getBOFactory();
        iVOFactory = jobInjection.getVOFactory();
        iCtx = jobInjection.getContextManager();
        iUser = new SuperUser(false);

        logger.info("********************** Clean upload temp dir START " + "**********************");

        // -- Suppression des fichiers
        removeFiles();

        // -- Suppression des uploads temporaires dans db
        removeFileDb();

        logger.info("********************** Clean upload temp dir END " + "**********************");
    }

    /**
     * Supprimer les fichiers dans le dossier temporaire
     *
     */
    private void removeFiles() {
        String tempDir = iCtx.getProperty("document.upload.temp");
        if (tempDir == null) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(tempDir))) {
            for (Path path : directoryStream) {
                files.add(path);
            }
            logger.info("Nbr of files to delete: " + files.size());
            for (Path file : files) {
                Files.delete(file);
            }
        } catch (IOException e) {
            logger.error("Error while scanning the files in directory " + tempDir, e);
        }
    }

    /**
     * Supprimer les lignes par utilisé dans table T_DOCUMENT
     *
     */
    private void removeFileDb() {
        IValueObject qVo = iVOFactory.getVO(Entity.DOCUMENT);
        qVo.setProperty("doc_obj_name", "UPLOAD");

        try (Connection con = iCtx.getDataSource().getConnection()) {
            List<IValueObject> lstRem = iBOFactory.getBO(Entity.DOCUMENT).getList(qVo, iUser, con).getListObject();
            logger.info("Nbr de fichier dans table t_document : " + lstRem.size());
            for (IValueObject vo : lstRem) {
                iBOFactory.getBO(Entity.DOCUMENT).delete(vo.getId(), vo.getTimestamp(), con, iUser, DAOParameter.EMPTY_PARAMETER);
            }

            con.commit();
        } catch (SQLException | ISException e) {
            logger.error("Erreur de consultation des documents à uploader", e);
        }

    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        logger.info("-------------- Clean upload dir job interrompu ---------------");
    }
}
