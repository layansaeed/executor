package sa.nrd.job.execute.service.bean;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sa.nrd.job.execute.job.BeanJob;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

@Component
@RequiredArgsConstructor
public class XmlFileWatcher {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private final BeanJob beanJob;

    @Value("${app.entities-config.path}")
    private String xmlFilePath;

    /**
     * Starts watching the XML file after the application is fully ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void watchFile() {
        Thread watcherThread = new Thread(() -> {
            try {
                Path filePath = Paths.get(xmlFilePath);
                Path directoryPath = filePath.getParent();

                WatchService watchService = FileSystems.getDefault().newWatchService();
                directoryPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                log.info("Watching XML file: {}", filePath);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey watchKey = watchService.take();

                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        if (event.context().toString().equals(filePath.getFileName().toString())) {
                            log.info("XML changed");
                            Thread.sleep(500);
                            beanJob.handleXmlChange();
                        }
                    }

                    boolean valid = watchKey.reset();
                    if (!valid) {
                        log.warn("Watch key is no longer valid. Stopping watch service.");
                        break;
                    }
                }

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Watcher thread interrupted. Stopping watcher.");

            } catch (Exception exception) {
                log.error("Watcher error", exception);
            }
        });

        watcherThread.setDaemon(true);
        watcherThread.start();
    }
}