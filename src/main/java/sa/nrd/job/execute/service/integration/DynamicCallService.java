package sa.nrd.job.execute.service.integration;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import sa.nrd.job.execute.constant.DynamicCallConstants;
import sa.nrd.job.execute.service.job.JobConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DynamicCallService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final RetryablePageExecutorService retryablePageExecutorService;
    private final JobConfigService jobConfigService;
    private final RestTemplate restTemplate;

    public DynamicCallService(RetryablePageExecutorService retryablePageExecutorService, JobConfigService jobConfigService, RestTemplate restTemplate) {
        this.retryablePageExecutorService = retryablePageExecutorService;

        this.jobConfigService = jobConfigService;
        this.restTemplate = restTemplate;
    }
    //when any retryable attempt success return here to call to another nin
    //just return false and stop the page after all max attempts failure
    public List<Map<String, Object>> callApis(String jobName, List<Long> nins) {

        try {
            Map<String, String> config = jobConfigService.getConfigMap(jobName);

            String url = buildUrl(config);
            HttpMethod httpMethod = buildHttpMethod(config);
            HttpHeaders headers = buildHeaders(config);

            List<Map<String, Object>> responses = new ArrayList<>();
            //object mutable
            AtomicInteger currentIndex = new AtomicInteger(0);

            //first: currentIndex=0 & responses = []
            //nins = [1 nin up to 25 NINs]

            //end of list or return false (all attempts failure)
            while (currentIndex.get() < nins.size()) {
                boolean shouldContinue =
                        retryablePageExecutorService.executeWithRetry(
                                jobName,
                                nins,
                                currentIndex,
                                url,
                                httpMethod,
                                headers,
                                responses
                        );

                if (!shouldContinue) {
                    break;
                    //stop the currect page and responses go back to processpage
                    // that has failure rows them mapping and insert
                }
//                if (!shouldContinue) {
//                    //When recover returns false, stop everything immediately.
//                    throw new MaxRetryAttemptsReachedException(
//                            "Max retry attempts reached for jobName: " + jobName
//                    );
//                }
            }

            return responses;

        } catch (Exception exception) {
            logger.debug("Failed before/while calling APIs for jobName [{}] exception [{}]",
                    jobName,
                    exception.getMessage());

            List<Map<String, Object>> responses = new ArrayList<>();
            responses.add(prepareLocalErrorResponse(exception));

            return responses;
        }
    }


   /**
     * Handles errors that happen before reaching the retryable API executor,
     * such as missing config, invalid HTTP method, invalid media type.
     */
    private Map<String, Object> prepareLocalErrorResponse(Exception exception) {
        Map<String, Object> responseBody = new LinkedHashMap<>();

        responseBody.put("failure", true);
        responseBody.put("message", exception.getMessage());
        responseBody.put("statusCode", null);
        responseBody.put("errorCode", null);

        return responseBody;
    }


    /**
     * Builds the request URL from configuration.
     */
    private String buildUrl(Map<String, String> config) {
        return getRequiredConfigValue(config, DynamicCallConstants.CONFIG_URL);
    }

    /**
     * Builds the HTTP method from configuration.
     */
    private HttpMethod buildHttpMethod(Map<String, String> config) {
        String httpMethodValue = getRequiredConfigValue(config, DynamicCallConstants.CONFIG_HTTP_METHOD);
        return HttpMethod.valueOf(httpMethodValue);
    }

    /**
     * Builds the request headers from configuration.
     */
    private HttpHeaders buildHeaders(Map<String, String> config) {
        String requiredConfigValue = getRequiredConfigValue(config, DynamicCallConstants.CONFIG_MEDIA_TYPE);
        //MediaType mediaType = MediaType.parseMediaType(requiredConfigValue);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        addConfiguredHeaders(headers, config);
        return headers;
    }

    /**
     * Adds all configured headers that start with the headers prefix.
     */
    private void addConfiguredHeaders(HttpHeaders headers, Map<String, String> config) {
        String headerPrefix = DynamicCallConstants.CONFIG_HEADERS_PREFIX;

        //loop all clean map
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String configKey = entry.getKey();

            if (!configKey.startsWith(headerPrefix)) {
                continue;
            }

            String headerName = configKey.substring(headerPrefix.length());
            String headerValue = entry.getValue();

            if (headerName.trim().isEmpty()) {
                throw new IllegalArgumentException("Header name must not be blank for config key: " + configKey);
            }

            if (headerValue == null || headerValue.trim().isEmpty()) {
                throw new IllegalArgumentException("Header value must not be blank for config key: " + configKey);
            }

            headers.set(headerName, headerValue);
        }
    }

    /**
     * Builds the request body.
     */
    private Map<String, Object> buildRequestBody(Long nin) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("nin", nin);
        return requestBody;
    }

    /**
     * Builds the HTTP entity from headers and body.
     */
    private HttpEntity<Map<String, Object>> buildRequestEntity(HttpHeaders headers,
                                                               Map<String, Object> requestBody) {
        return new HttpEntity<>(requestBody, headers);
    }

    /**
     * Returns a required configuration value.
     */
    private String getRequiredConfigValue(Map<String, String> config, String key) {
        String value = config.get(key);

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required config key: " + key);
        }

        return value;
    }

}