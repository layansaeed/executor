package sa.nrd.job.execute.job;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sa.nrd.job.execute.config.Config;
import sa.nrd.job.execute.service.bean.EntityDefinitionRegistry;
import sa.nrd.job.execute.service.bean.XMLBeanLoaderService;

@Component
public class BeanJob {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final XMLBeanLoaderService xmlLoader;
    private final EntityDefinitionRegistry registry;
    private final Config.PropertySourceConfig propertySourceConfig;


    private boolean jobRunning;
    private boolean reloadPending;

    public BeanJob(XMLBeanLoaderService xmlLoader, EntityDefinitionRegistry registry, Config.PropertySourceConfig propertySourceConfig) {
        this.xmlLoader = xmlLoader;
        this.registry = registry;
        this.propertySourceConfig = propertySourceConfig;
    }

    /**
     * Loads XML entity definitions once when the application starts.
     */
    @PostConstruct
    public void run() {
        try {
            xmlLoader.loadBeansFromXML();
        } catch (Exception exception) {
            logger.error("Startup failed: {}", exception.getMessage(), exception);
            throw new RuntimeException("Startup failed", exception);
        }
    }

    /**
     * Marks that a job has started.
     */
    public void markJobStarted() {
        jobRunning = true;
        logger.info("Job execution started");
    }

    /**
     * Marks that a job has finished and reloads XML if a reload is pending.
     */
    public void markJobFinished() {
        jobRunning = false;
        logger.info("Job execution finished");

        if (reloadPending) {
            logger.info("Reload pending detected after job completion. Reloading XML now...");
            reloadPending = false;
            reloadBeans();
        }
    }

    /**
     * Handles XML change request safely.
     * If a job is running, reload is postponed.
     * Otherwise, reload happens immediately.
     */
    public void handleXmlChange() {
        if (jobRunning) {
            reloadPending = true;
            logger.info("XML change detected during active job. Reload postponed until job completion.");
            return;
        }
        reloadBeans();
    }

    /**
     * Reloads XML entity definitions into memory.
     */
    public void reloadBeans() {
        try {
            logger.info("Starting full reload for DB config and XML definitions");

            //reload latest DB config from config table
            propertySourceConfig.reloadDatabaseProperties();

            logger.info("Before delete: Loaded {} entity definition(s)", registry.getAll().size());

            //clear old XML entity definitions
            registry.getAll().clear();
            logger.info("After delete: Loaded {} entity definition(s)", registry.getAll().size());

            //reload new XML entity definitions
            xmlLoader.loadBeansFromXML();

            logger.info("After load: Loaded {} entity definition(s)", registry.getAll().size());

        } catch (Exception exception) {
            logger.error("Reload failed: {}", exception.getMessage(), exception);
            throw new RuntimeException("Reload failed", exception);
        }
    }

//
//    /**
//     * Returns whether a job is currently running.
//     *
//     * @return true if a job is running, otherwise false
//     */
//
//    public boolean isJobRunning() {
//        return jobRunning;
//    }
//
//    /**
//     * Returns whether XML reload is pending.
//     *
//     * @return true if reload is pending, otherwise false
//     */
//    public boolean isReloadPending() {
//        return reloadPending;
//    }
}