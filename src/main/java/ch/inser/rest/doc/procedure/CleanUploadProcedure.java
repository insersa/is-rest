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

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.rest.quartz.JobInjection;
import ch.inser.rest.util.Constants.ProcedureResult;
import ch.inser.rest.util.ServiceLocator;

/**
 * Vide régulierement le répértoire temporaire de upload de fichiers
 *
 * @author INSER SA *
 */
public class CleanUploadProcedure {

    /**
     * Définition du Logger utilisé pour le logging.
     */
    private static final Log logger = LogFactory.getLog(CleanUploadProcedure.class);

    /**
     * Programme un job périodique qui supprime les fichiers du répértoire
     * temporaire et la métadata (et blob, si mode "stockage BD") de la base de
     * données qui n'ont pas été lié à un objet métier
     *
     * @return started_no_message ou already_running
     * @throws SchedulerException
     *             erreur de scheduling de Quartz
     */
    public ProcedureResult cleanUploadDir() throws SchedulerException {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        if (ctx.getScheduler().getJobGroupNames().contains("CLEAN_UPLOAD_DIR")) {
            logger.info("Job 'Nettoyage périodique de répértoire temporaire de upload' déjà en cours");
            return ProcedureResult.ALREADY_RUNNING;
        }

        // specify job details
        JobDetail job = JobBuilder.newJob(CleanUploadDirJob.class).withIdentity("CLEAN_UPLOAD_DIR", "CLEAN_UPLOAD_DIR").build();
        Map<String, Object> dataMap = job.getJobDataMap();
        dataMap.put("jobInjection", new JobInjection());

        // Specify trigger
        String interval = ServiceLocator.getInstance().getContextManager().getProperty("document.upload.clean");
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("CLEAN_UPLOAD_DIR", "cleanTriggers")
                .withSchedule(CronScheduleBuilder.cronSchedule(interval)).forJob(job).build();

        // Schedule job
        Scheduler scheduler = ctx.getScheduler();
        scheduler.scheduleJob(job, trigger);
        return ProcedureResult.STARTED;
    }

}
