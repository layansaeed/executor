package sa.nrd.job.execute.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RetryablePageExecutorService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final RestTemplate restTemplate;

    public RetryablePageExecutorService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Executes API calls using a list of NINs.
     *
     * If retryable error happens, Spring Retry calls this method again.
     * Because currentIndex is already increased, the next retry uses the next NIN.
     */
    @Retryable(
            retryFor = {
                    HttpServerErrorException.class,
                    ResourceAccessException.class
            },
            noRetryFor = {
                    HttpClientErrorException.BadRequest.class,
                    HttpClientErrorException.Unauthorized.class
            },
            maxAttemptsExpression = "${job.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${job.retry.delay-ms}")
    )
    public boolean executeWithRetry(String jobName,
                                    List<Long> nins,
                                    AtomicInteger currentIndex,
                                    String url,
                                    HttpMethod httpMethod,
                                    HttpHeaders headers,
                                    List<Map<String, Object>> responses) {

        //end of list (page) do not crash with IndexOutOfBoundsException and return false
        //If the index already reached the list size, we return false instead of throwing IndexOutOfBoundsException
        if (currentIndex.get() >= nins.size()) { //indexes: 0-> 24. no 25 same as list size
            logger.info("No more NINs to process for jobName={}", jobName);
            return false;
        }

        //retryable executor takes the current NIN and increments the index before the API call
        //currentIndex.get() = same index so same nin
        /**
         * 1. get current value first
         * 2. use it to read NIN
         * 3. then increase the index
         * nins.get(0) is used
         * then currentIndex becomes 1
         *
         * If NIN at index 0 fails and retry happens:
         * Before attempt 2:
         * currentIndex = 1
         *
         * During attempt 2:
         * nin = nins.get(1)
         * currentIndex becomes 2
         * call API for nins[1]
         *
         * attempt 3 : index=2
         * executeWithRetry called
         * take NIN index 2
         * currentIndex becomes 3
         * API success
         * return true
         * back to while loop with index 3
         * executeWithRetry called again
         * take NIN index 3
         * currentIndex becomes 4
         * API called
         */
        Long nin = nins.get(currentIndex.getAndIncrement());

        // If the call fails with a retryable error, Spring retries the same method,
        // but the index already points to the next NIN.
        // So the retry attempt uses the next NIN, not the same one.
        try {
            logger.info("Calling API for jobName={} url={} nin={}",
                    jobName,
                    url,
                    nin);

            HttpEntity<Map<String, Object>> requestEntity =
                    new HttpEntity<>(buildRequestBody(nin), headers);

            ResponseEntity<Map> restResponse =
                    restTemplate.exchange(url, httpMethod, requestEntity, Map.class);

            responses.add(prepareSuccessResponse(nin, restResponse));

            logger.debug("Successfully retrieved response for jobName={} nin={}",
                    jobName,
                    nin);

            return true;

        } catch (Exception exception) {
            //add new failure row into list then throw exception
            responses.add(prepareErrorResponse(nin, exception));

            //Inside the catch, you must rethrow retryable exceptions cus
            //catch everything and only return true, then @Retryable will not retry
            if (isRetryableException(exception)) {
                logger.warn("Retryable error for jobName={} nin={} errorCode={} message={}",
                        jobName,
                        nin,
                        getErrorCode(exception),
                        getErrorMessage(exception));

                rethrowRetryableException(exception);
                //here in case RetryableException not return true and retry another attempt with new index -> new nin
            }

            logger.warn("Error without retry for jobName={} nin={} errorCode={} message={}",
                    jobName,
                    nin,
                    getErrorCode(exception),
                    getErrorMessage(exception));

            return true;
        }
    }

    /**
     * Recovery after Spring tried all maxAttempts and all attempts failed with retryable errors
     * Failed NIN rows are already added in the catch block.
     * This method only marks the last saved row as max retry reached.
     * Now stop retrying and call recover()
     */
    @Recover
    public boolean recover(Exception exception,
                           String jobName,
                           List<Long> nins,
                           AtomicInteger currentIndex,
                           String url,
                           HttpMethod httpMethod,
                           HttpHeaders headers,
                           List<Map<String, Object>> responses) {

        Long lastFailedNin = getLastFailedNin(nins, currentIndex);

        logger.error("Max retry attempts reached for jobName={} lastFailedNin={} errorCode={} message={}",
                jobName,
                lastFailedNin,
                getErrorCode(exception),
                getErrorMessage(exception),
                exception);

        /**
         * the failed row was already saved into responses inside the catch block before rethrowing.
         * So @Recover does not create a new error row.
         * It only modifies the last saved error row.
         */
        //markLastResponseAsMaxAttemptsReached(responses);

        return false;
    }

    /**
     * Builds request body for current NIN.
     */
    private Map<String, Object> buildRequestBody(Long nin) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("nin", nin);
        return requestBody;
    }

    /**
     * Prepares success response map.
     */
    private Map<String, Object> prepareSuccessResponse(Long nin,
                                                       ResponseEntity<Map> restResponse) {
        Map<String, Object> responseBody;

        if (restResponse.getBody() == null) {
            responseBody = new LinkedHashMap<>();
        } else {
            responseBody = new LinkedHashMap<>(restResponse.getBody());
        }

        responseBody.putIfAbsent("nin", nin);
        responseBody.put("failure", false);
        responseBody.put("message", null);
        responseBody.put("statusCode", String.valueOf(restResponse.getStatusCode().value()));
        responseBody.put("errorCode", null);

        return responseBody;
    }

    /**
     * Prepares error response map for all exception types.
     */
    private Map<String, Object> prepareErrorResponse(Long nin,
                                                     Exception exception) {
        Map<String, Object> responseBody = new LinkedHashMap<>();

        responseBody.put("nin", nin);
        responseBody.put("failure", true);
        responseBody.put("message", getErrorMessage(exception));
        responseBody.put("statusCode", getStatusCode(exception));
        responseBody.put("errorCode", getErrorCode(exception));

        return responseBody;
    }

    /**
     * Checks if exception should trigger retry.
     */
    private boolean isRetryableException(Exception exception) {
        return exception instanceof HttpServerErrorException
                || exception instanceof ResourceAccessException;
    }

    /**
     * Rethrows retryable exceptions to allow @Retryable to work.
     */
    private void rethrowRetryableException(Exception exception) {
        if (exception instanceof HttpServerErrorException serverException) {
            throw serverException;
        }

        if (exception instanceof ResourceAccessException resourceException) {
            throw resourceException;
        }
    }

    /**
     * Gets error message from response body if available.
     * HTTP error + body exists      -> return response body
     * HTTP error + body empty       -> return exception message
     * Non-HTTP error
     */
    private String getErrorMessage(Exception exception) {
        if (exception instanceof HttpStatusCodeException httpException) {
            String responseBody = httpException.getResponseBodyAsString();

            if (responseBody != null && !responseBody.trim().isEmpty()) {
                return responseBody;
            }
        }

        return exception.getMessage();
    }

    /**
     * Gets HTTP status code if available.
     * HTTP error      -> return status code as String
     * Non-HTTP error  -> return null
     */
    private String getStatusCode(Exception exception) {
        if (exception instanceof HttpStatusCodeException httpException) {
            return String.valueOf(httpException.getStatusCode().value());
        }

        return null;
    }

    /**
     * Gets dynamic error code from HTTP status or exception class name.
     * HTTP error      -> return "500 INTERNAL_SERVER_ERROR" / "400 BAD_REQUEST"
     * Non-HTTP error  -> return exception class name
     */
    private String getErrorCode(Exception exception) {
        if (exception instanceof HttpStatusCodeException httpException) {
            return httpException.getStatusCode().toString();
        }
        return exception.getClass().getSimpleName();
    }

    /**
     * Gets last failed NIN based on current index.
     */
    private Long getLastFailedNin(List<Long> nins,
                                  AtomicInteger currentIndex) {
        //-1 : currentIndex was already increased before calling the API
        int lastIndex = currentIndex.get() - 1;

        if (lastIndex < 0 || lastIndex >= nins.size()) {
            return null;
        }

        return nins.get(lastIndex);
    }

    /**
     * Marks last error response as max retry reached.
     */
    private void markLastResponseAsMaxAttemptsReached(List<Map<String, Object>> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        Map<String, Object> lastResponse = responses.get(responses.size() - 1);
        Object oldMessage = lastResponse.get("message");

        lastResponse.put("failure", true);
        lastResponse.put("message", "Max retry attempts reached. Last error: " + oldMessage);
    }

//
//    /**
//     * Prepares final map for error case
//     * @param exception current exception
//     * @return error response map
//     * Cons of manual way
//     * 1. More code.
//     * 2. You check the type, then manually cast.
//     * 3. More chance to cast wrong by mistake.
//     * example if (e instanceof HttpClientErrorException) {
//     *     HttpServerErrorException httpEx = (HttpServerErrorException) e; // runtime error
//     * }
//     * 4. Less readable
//     */
//    public Map<String, Object> prepareErrorResponse(Exception exception) {
//        Map<String, Object> responseBody = new LinkedHashMap<>();
//
//        //pattern matching: check type(if true) -> create httpEx automatically & cast then use httpEx in one line
//        if (exception instanceof HttpStatusCodeException httpEx) {
//
//            //normal way:  check + manual cast
////        if (exception instanceof HttpClientErrorException
////                || exception instanceof HttpServerErrorException) {
////            HttpStatusCodeException httpEx = (HttpStatusCodeException) exception;
//            responseBody.put("failure", true);
//            responseBody.put("message", httpEx.getResponseBodyAsString());
//            responseBody.put("statusCode", String.valueOf(httpEx.getRawStatusCode()));
//            responseBody.put("errorCode", null);
//            return responseBody;
//        }
//
//        responseBody.put("failure", true);
//        responseBody.put("message", exception.getMessage());
//        responseBody.put("statusCode", null);
//        responseBody.put("errorCode", null);
//
//        return responseBody;
//    }
}
//
//    /**
//     * Prepares final map for error case.
//     *
//     * @param exception current exception
//     * @return error response map
//     */
//    public Map<String, Object> prepareErrorResponse(Exception exception) {
//        Map<String, Object> responseBody = new LinkedHashMap<>();
//
//        //if (exception instanceof HttpStatusCodeException httpEx) {
//        if (exception instanceof HttpClientErrorException
//                || exception instanceof HttpServerErrorException) {
//
//            HttpStatusCodeException httpEx = (HttpStatusCodeException) exception;
//            responseBody.put("failure", true);
//            responseBody.put("message", httpEx.getResponseBodyAsString());
//            responseBody.put("statusCode", String.valueOf(httpEx.getRawStatusCode()));
//            responseBody.put("errorCode", null);
//            return responseBody;
//        }
//
//        responseBody.put("failure", true);
//        responseBody.put("message", exception.getMessage());
//        responseBody.put("statusCode", null);
//        responseBody.put("errorCode", null);
//
//        return responseBody;
//    }

