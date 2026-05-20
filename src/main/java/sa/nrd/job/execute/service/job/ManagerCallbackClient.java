package sa.nrd.job.execute.service.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
@Service
public class ManagerCallbackClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${job.manager.base-url}")
    private String managerBaseUrl;

//    @Value("${job.executor.server-name}")
//    private String serverName;

    @Value("${server.port}")
    private String serverPort;

    /**
     * Runs after Executor application is fully ready.
     * It notifies Manager that this Executor server is available.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void notifyManagerWhenApplicationReady() {
        logger.info("Executor started. serverPort={}", serverPort);

        notifyExecutorAvailable(serverPort);
    }

    /**
     * Notifies Manager that this Executor server is available.
     *
     * @param serverPort executor server name
     */
    public void notifyExecutorAvailable(String serverPort) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(managerBaseUrl)
                    .path("/v1/job-manager/executor-servers/{serverName}/available")
                    .buildAndExpand(serverPort)
                    .toUri();

            logger.info("Calling Manager executor-available URL: {}", uri);

            restTemplate.postForEntity(uri, null, Void.class);

        }catch (Exception ex) {
            logger.error("Failed to notify Manager that executor port={} is available. Error={}",
                    serverPort,
                    ex.getMessage(),
                    ex);
        }
    }

    /**
     * Notifies Manager that execution processing is finished.
     *
     * @param executionId finished execution identifier
     */
    public void notifyExecutionFinished(Long executionId) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(managerBaseUrl)
                    .path("/v1/job-manager/{executionId}/finished")
                    .buildAndExpand(executionId)
                    .toUri();

            logger.info("Calling Manager callback URL: {}", uri);

            restTemplate.postForEntity(uri, null, Void.class);

        } catch (Exception ex) {
            logger.error("Failed to notify Manager for executionId={}. Error={}",
                    executionId,
                    ex.getMessage(),
                    ex);
        }
    }
}