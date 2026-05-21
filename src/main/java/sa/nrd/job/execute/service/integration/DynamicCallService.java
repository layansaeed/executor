package sa.nrd.job.execute.service.integration;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import sa.nrd.job.execute.constant.DynamicCallConstants;
import sa.nrd.job.execute.exception.MaxRetryAttemptsReachedException;
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

    //when any retryable attempt success return here to call to another [page of nin]
    //stop the page after return false all max attempts failure /return success: finish all attempts
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
            //one page-> nins = [1 nin up to 25 NINs]

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

//                if (!shouldContinue) {
//                    break;
//                    //stop the current page and responses go back to processpage
//                    // that has failure rows them mapping and insert
//                }
                // When @Recover returns false, throw a fatal exception to stop the whole job.
                if (!shouldContinue) {
                    throw new MaxRetryAttemptsReachedException(
                            "Max retry attempts reached for jobName: " + jobName,
                            responses
                    );
                }
            }

            return responses;

        } catch (MaxRetryAttemptsReachedException exception) {
            // Do not convert this to local error response.
            // This exception is used to stop the whole job.
            throw exception;
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


//    /**
//     * Calls the external API for the given job and NIN.
//     * Always returns one map ready for storing.
//     */
//    public Map<String, Object> callApi(String jobName, Long nin) {
//            Map<String, String> config = jobConfigService.getConfigMap(jobName);
//
//            String url = buildUrl(config);
//            HttpMethod httpMethod = buildHttpMethod(config);
//            HttpHeaders headers = buildHeaders(config);
//            Map<String, Object> requestBody = buildRequestBody(nin);
//            HttpEntity<Map<String, Object>> requestEntity = buildRequestEntity(headers, requestBody);
//
//            ResponseEntity<Map> restResponse =
//                    restTemplate.exchange(url, httpMethod, requestEntity, Map.class);
//
//            logger.debug("Successfully retrieved response for jobName={} nin={}",
//                    jobName,
//                    nin);
//
//            return prepareSuccessResponse(restResponse);
//
//    }
//
//    private Map<String, Object> prepareSuccessResponse(ResponseEntity<Map> restResponse) {
//        Map<String, Object> responseBody;
//
//        if (restResponse.getBody() == null) {
//            responseBody = new LinkedHashMap<>();
//        } else {
//            responseBody = new LinkedHashMap<>(restResponse.getBody());
//        }
//
//       // responseBody.putIfAbsent("nin", nin);
//        responseBody.put("failure", false);
//        responseBody.put("message", null);
//        responseBody.put("errorCode", null);
//        responseBody.put("statusCode", String.valueOf(restResponse.getStatusCode().value()));
//
//        return responseBody;
//    }

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
     * Executes the external API request.
     * Does not swallow the exception.
     */
//    private ResponseEntity<Map> executeRequest(String jobName,
//                                               Long nin,
//                                               String url,
//                                               HttpMethod httpMethod,
//                                               HttpEntity<Map<String, Object>> requestEntity) {
//        logger.info("Calling API for jobName={} url={} nin={}", jobName, url, nin);
//        //Thread.sleep(500);
//        return restTemplate.exchange(url, httpMethod, requestEntity, Map.class);
//    }


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

    //    /**
//     * Prepares final map for error case.
//     *
//     * @param exception current exception
//     * @return error response map
//     */
//    public Map<String, Object> prepareErrorResponse(Exception exception) {
//        Map<String, Object> responseBody = new LinkedHashMap<>();
//
//        Throwable actualException = exception;
//
//        if (exception instanceof RetryableIntegrationException && exception.getCause() != null) {
//            actualException = exception.getCause();
//        }
//
//        //RetryableIntegrationException -> cause = HttpServerErrorException
//        //unwraps exception and sees the real cause
//
//        //if (actualException instanceof HttpStatusCodeException httpEx) {
//        if (actualException instanceof HttpClientErrorException
//                || actualException instanceof HttpServerErrorException) {
//
//            HttpStatusCodeException httpEx = (HttpStatusCodeException) actualException;
//            responseBody.put("failure", true);
//            responseBody.put("message", httpEx.getResponseBodyAsString());
//            responseBody.put("statusCode", String.valueOf(httpEx.getRawStatusCode()));
//            responseBody.put("errorCode", null);
//            return responseBody;
//        }
//
//        responseBody.put("failure", true);
//        responseBody.put("message", actualException.getMessage());
//        responseBody.put("statusCode", null);
//        responseBody.put("errorCode", null);
//
//        return responseBody;
//    }
//
//
}