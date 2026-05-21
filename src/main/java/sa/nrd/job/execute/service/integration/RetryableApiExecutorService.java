//package sa.nrd.job.execute.service.integration;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.ResponseEntity;
//import org.springframework.retry.annotation.Backoff;
//import org.springframework.retry.annotation.Recover;
//import org.springframework.retry.annotation.Retryable;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.HttpServerErrorException;
//import org.springframework.web.client.HttpStatusCodeException;
//import org.springframework.web.client.ResourceAccessException;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.LinkedHashMap;
//import java.util.Map;
//
///**
// * NIN 111 fails
// * retry NIN 111
// * retry NIN 111
// * then @Recover
// * about same nin level
// */
//@Service
//public class RetryableApiExecutorService {
//
//    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
//
//    private final RestTemplate restTemplate;
//
//    public RetryableApiExecutorService(RestTemplate restTemplate) {
//        this.restTemplate = restTemplate;
//    }
//
//    /**
//     * Executes external API call.
//     *
//     * Retry will happen only for temporary failures:
//     * - HttpServerErrorException: 5xx errors
//     * - ResourceAccessException: timeout, connection issue, DNS issue, etc.
//     *
//     * Client errors like 400 and 401 will not be retried.
//     */
//    @Retryable(
//            retryFor = {
//                    HttpServerErrorException.class,
//                    ResourceAccessException.class
//            },
//            noRetryFor = {
//                    HttpClientErrorException.BadRequest.class,
//                    HttpClientErrorException.Unauthorized.class
//            },
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 2000)
//    )
//    public Map<String, Object> executeWithRetry(String jobName,
//                                                Long nin,
//                                                String url,
//                                                HttpMethod httpMethod,
//                                                HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.info("Calling API for jobName={} url={} nin={}", jobName, url, nin);
//
//        ResponseEntity<Map> restResponse =
//                restTemplate.exchange(url, httpMethod, requestEntity, Map.class);
//
//        logger.debug("Successfully retrieved response for jobName={} nin={}", jobName, nin);
//
//        return prepareSuccessResponse(restResponse);
//    }
//
//    /**
//     * Recovery for 5xx errors after all retry attempts are exhausted.
//     */
//    @Recover
//    public Map<String, Object> recover(HttpServerErrorException exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.error("Server error after retries for jobName={} nin={} status={} body={}",
//                jobName,
//                nin,
//                exception.getStatusCode(),
//                exception.getResponseBodyAsString());
//
//        return prepareHttpErrorResponse(exception);
//    }
//
//    /**
//     * Recovery for timeout / connection errors after all retry attempts are exhausted
//     */
//    @Recover
//    public Map<String, Object> recover(ResourceAccessException exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.error("Resource access error after retries for jobName={} nin={} error={}",
//                jobName,
//                nin,
//                exception.getMessage());
//
//        return prepareGenericErrorResponse(exception);
//    }
//
//    /**
//     * Recovery for 400 Bad Request.
//     *
//     * Because BadRequest is inside noRetryFor, it will not retry.
//     * It will come here directly.
//     */
//    @Recover
//    public Map<String, Object> recover(HttpClientErrorException.BadRequest exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.warn("Bad request without retry for jobName={} nin={} body={}",
//                jobName,
//                nin,
//                exception.getResponseBodyAsString());
//
//        return prepareHttpErrorResponse(exception);
//    }
//
//    /**
//     * Recovery for 401 Unauthorized.
//     *
//     * Because Unauthorized is inside noRetryFor, it will not retry.
//     * It will come here directly.
//     */
//    @Recover
//    public Map<String, Object> recover(HttpClientErrorException.Unauthorized exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.warn("Unauthorized without retry for jobName={} nin={} body={}",
//                jobName,
//                nin,
//                exception.getResponseBodyAsString());
//
//        return prepareHttpErrorResponse(exception);
//    }
//
//    /**
//     * Fallback recovery for other client errors if they reach recovery.
//     * Example: 403, 404, 409, 422.
//     */
//    @Recover
//    public Map<String, Object> recover(HttpClientErrorException exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.warn("Client error without retry for jobName={} nin={} status={} body={}",
//                jobName,
//                nin,
//                exception.getStatusCode(),
//                exception.getResponseBodyAsString());
//
//        return prepareHttpErrorResponse(exception);
//    }
//
//    /**
//     * Final fallback recovery.
//     */
//    @Recover
//    public Map<String, Object> recover(Exception exception,
//                                       String jobName,
//                                       Long nin,
//                                       String url,
//                                       HttpMethod httpMethod,
//                                       HttpEntity<Map<String, Object>> requestEntity) {
//
//        logger.error("Unexpected integration error for jobName={} nin={} error={}",
//                jobName,
//                nin,
//                exception.getMessage());
//
//        return prepareGenericErrorResponse(exception);
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
//        responseBody.put("failure", false);
//        responseBody.put("message", null);
//        responseBody.put("errorCode", null);
//        responseBody.put("statusCode", String.valueOf(restResponse.getStatusCode().value()));
//
//        return responseBody;
//    }
//
//    private Map<String, Object> prepareHttpErrorResponse(HttpStatusCodeException exception) {
//        Map<String, Object> responseBody = new LinkedHashMap<>();
//
//        responseBody.put("failure", true);
//        responseBody.put("message", exception.getResponseBodyAsString());
//        responseBody.put("statusCode", String.valueOf(exception.getStatusCode().value()));
//        responseBody.put("errorCode", null);
//
//        return responseBody;
//    }
//
//    private Map<String, Object> prepareGenericErrorResponse(Exception exception) {
//        Map<String, Object> responseBody = new LinkedHashMap<>();
//
//        responseBody.put("failure", true);
//        responseBody.put("message", exception.getMessage());
//        responseBody.put("statusCode", null);
//        responseBody.put("errorCode", null);
//
//        return responseBody;
//    }
//}
//
////
///**
//// * Calls the external API for the given job and NIN.
//// * Always returns one map ready for storing.
//// */
////public Map<String, Object> callApi(String jobName, Long nin) {
////    try {
////        Map<String, String> config = jobConfigService.getConfigMap(jobName);
////
////        String url = buildUrl(config);
////        HttpMethod httpMethod = buildHttpMethod(config);
////        HttpHeaders headers = buildHeaders(config);
////        Map<String, Object> requestBody = buildRequestBody(nin);
////        HttpEntity<Map<String, Object>> requestEntity = buildRequestEntity(headers, requestBody);
////
////            return retryableApiExecutorService.executeWithRetry(
////                    jobName,
////                    nin,
////                    url,
////                    httpMethod,
////                    requestEntity
////            );
////
////    } catch (Exception exception) {
////        logger.debug("Failed before/while calling API for jobName [{}] nin [{}] exception [{}]",
////                jobName,
////                nin,
////                exception.getMessage());
////
////        return prepareLocalErrorResponse(exception);
////        //callApi(...) still returns a Map-> So it will not go to the CompletableFuture catch.
////        //prepareLocalErrorResponse(...) prevents the outer catch in CompletableFuture from running.
////    }
////}
////   /**
////     * Handles errors that happen before reaching the retryable API executor,
////     * such as missing config, invalid HTTP method, invalid media type.
////     */
////    private Map<String, Object> prepareLocalErrorResponse(Exception exception) {
////        Map<String, Object> responseBody = new LinkedHashMap<>();
////
////        responseBody.put("failure", true);
////        responseBody.put("message", exception.getMessage());
////        responseBody.put("statusCode", null);
////        responseBody.put("errorCode", null);
////
////        return responseBody;
////    }