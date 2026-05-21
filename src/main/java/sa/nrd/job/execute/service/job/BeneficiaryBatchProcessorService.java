package sa.nrd.job.execute.service.job;

import sa.nrd.job.execute.exception.MaxRetryAttemptsReachedException;
import sa.nrd.job.execute.model.beneficiary.BeneficiaryEntity;
import sa.nrd.job.execute.model.manage.EntityDefinition;
import sa.nrd.job.execute.repository.BeneficiaryJpaRepository;
import sa.nrd.job.execute.repository.GenericEntityRepository;
import sa.nrd.job.execute.service.bean.EntityDefinitionRegistry;
import sa.nrd.job.execute.service.integration.DynamicCallService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class BeneficiaryBatchProcessorService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final ExecutorService executorService;
    private final int databaseChunkSize;
    private final long rangeChunkSize;

    private final BeneficiaryJpaRepository beneficiaryRepository;
    private final DynamicCallService dynamicCallService;
    private final EntityDefinitionRegistry entityDefinitionRegistry;
    private final GenericEntityRepository genericEntityRepository;

    /**
     * Creates the service with required dependencies and executor.
     *
     * @param databaseChunkSize number of records per page
     * @param rangeChunkSize number of values per range chunk
     * @param beneficiaryRepository repository for beneficiary records
     * @param dynamicCallService service used to call external API
     * @param entityDefinitionRegistry registry for entity definitions
     * @param genericEntityRepository repository for dynamic insert operations
     */
    public BeneficiaryBatchProcessorService(
            @Value("${job.db.chunk}") int databaseChunkSize,
            @Value("${job.range.chunk}") long rangeChunkSize,
            BeneficiaryJpaRepository beneficiaryRepository,
            DynamicCallService dynamicCallService,
            EntityDefinitionRegistry entityDefinitionRegistry,
            GenericEntityRepository genericEntityRepository
    ) {
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.databaseChunkSize = databaseChunkSize;
        this.rangeChunkSize = rangeChunkSize;
        this.beneficiaryRepository = beneficiaryRepository;
        this.dynamicCallService = dynamicCallService;
        this.entityDefinitionRegistry = entityDefinitionRegistry;
        this.genericEntityRepository = genericEntityRepository;
    }

    /**
     * Processes all records within the full input range.
     *
     * @param jobName job name used to load configuration
     * @param start range start value
     * @param end range end value
     */
    public void processByRange(String jobName, Long start, Long end) {
        validateJobName(jobName);
        validateRange(start, end);

        EntityDefinition entityDefinition = entityDefinitionRegistry.get(jobName);

        logger.info("Starting parallel processing for jobName={} rangeStart={} rangeEnd={}",
                jobName, start, end);

        long currentRangeStart = start;

        while (currentRangeStart <= end) {
            long currentRangeEnd = currentRangeStart + rangeChunkSize - 1;

            if (currentRangeEnd > end) {
                currentRangeEnd = end;
            }

            processChunk(jobName,
                    entityDefinition,
                    currentRangeStart,
                    currentRangeEnd);
            currentRangeStart = currentRangeEnd + 1;
        }

        logger.info("Completed parallel processing for jobName={} rangeStart={} rangeEnd={}",
                jobName, start, end);
    }

    /**
     * Processes one range chunk page by page.
     *
     * @param jobName job name used for processing
     * @param entityDefinition entity definition for the target table
     * @param rangeStart current range chunk start
     * @param rangeEnd current range chunk end
     */
    private void processChunk(String jobName,
                              EntityDefinition entityDefinition,
                              Long rangeStart,
                              Long rangeEnd) {

        int pageNumber = 0;

        logger.info("Processing range window {} - {} for jobName={}",
                rangeStart, rangeEnd, jobName);

        while (true) {
            Page<BeneficiaryEntity> beneficiaryPage =
                    beneficiaryRepository.findBeneficiaries(
                    PageRequest.of(pageNumber, databaseChunkSize),
                    rangeStart,
                    rangeEnd
            );

            if (!beneficiaryPage.hasContent()) {
                logger.info("No more beneficiaries in range window {} - {} for jobName={}",
                        rangeStart, rangeEnd, jobName);
                break;
            }

            processPage(jobName,
                    entityDefinition,
                    beneficiaryPage,
                    pageNumber);
            pageNumber++;
        }
    }

    private void processPage(String jobName,
                             EntityDefinition entityDefinition,
                             Page<BeneficiaryEntity> beneficiaryPage,
                             int pageNumber) {

        logger.info("Processing page {} for jobName={} with {} beneficiary record(s)",
                pageNumber, jobName, beneficiaryPage.getNumberOfElements());

        List<Long> nins = new ArrayList<>();

        for (BeneficiaryEntity beneficiary : beneficiaryPage.getContent()) {
            nins.add(beneficiary.getNin());
        }

        //one CompletableFuture = one page list of NINs
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();

        futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                List<Map<String, Object>> responses =
                        dynamicCallService.callApis(jobName, nins);

                List<Map<String, Object>> rows = new ArrayList<>();

                for (Map<String, Object> response : responses) {
                    rows.add(toEntityRow(entityDefinition, response));
                }

                return rows;

            } catch (MaxRetryAttemptsReachedException exception) {
                List<Map<String, Object>> rows = new ArrayList<>();

                for (Map<String, Object> response : exception.getResponses()) {
                    rows.add(toEntityRow(entityDefinition, response));
                }

                if (!rows.isEmpty()) {
                    genericEntityRepository.insertAllRows(entityDefinition, rows);

                    logger.info("Saved {} failed row(s) for jobName={} in page={} before stopping job",
                            rows.size(),
                            jobName,
                            pageNumber);
                }

                throw exception;
            }catch (Exception exception) {
                logger.error("Failed processing page={} for jobName={}. Error={}",
                        pageNumber, jobName, exception.getMessage(), exception);

                return new ArrayList<>();
            }

        }, executorService));

        /**
         *  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
         *  or try / catch
         *  exception happens inside CompletableFuture, Java wraps it inside CompletionException.
         * may throw CompletionException.
         *
         * If you do not catch it, the job will stop. That is okay.
         *
         * But if you want cleaner logging, you can write:
         *
         */
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//        try {
//            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//        } catch (CompletionException exception) {
//            if (exception.getCause() instanceof MaxRetryAttemptsReachedException maxRetryException) {
//                throw maxRetryException;
//            }
//
//            throw exception;
//        }

        List<Map<String, Object>> pageResults = new ArrayList<>();

        for (CompletableFuture<List<Map<String, Object>>> future : futures) {
            List<Map<String, Object>> result = future.join();

            if (result != null && !result.isEmpty()) {
                pageResults.addAll(result);
            }
        }

        if (!pageResults.isEmpty()) {
            genericEntityRepository.insertAllRows(entityDefinition, pageResults);

            logger.info("Saved {} row(s) for jobName={} in page={}",
                    pageResults.size(), jobName, pageNumber);
        }
    }
//    /**
//     * Processes one page of beneficiaries in parallel.
//     * shared method btw processChunk & fetchAndProcessAllPages -> split pages then processBeneficiary in each page
//     * @param jobName job name used for processing
//     * @param entityDefinition entity definition for the target table
//     * @param beneficiaryPage current beneficiary page
//     * @param pageNumber current page number
//     */
//    private void processPage(String jobName,
//                             EntityDefinition entityDefinition,
//                             Page<BeneficiaryEntity> beneficiaryPage,
//                             int pageNumber) {
//
//        logger.info("Processing page {} for jobName={} with {} beneficiary record(s)",
//                pageNumber, jobName, beneficiaryPage.getNumberOfElements());
//
//        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
//
//        //page list from beneficiaryPage.getContent() and currently creates one future per NIN
//        for (BeneficiaryEntity beneficiary : beneficiaryPage.getContent()) {
//            Long nin = beneficiary.getNin();
//
//            futures.add(CompletableFuture.supplyAsync(() -> {
//                try {
//                    Map<String, Object> response =
//                            dynamicCallService.callApi(jobName, nin);
//                    return toEntityRow(entityDefinition, response);
//
//                } catch (Exception exception) {
//                    // error happens → not handled inside callApi → exception thrown outside
//                    logger.error("Failed processing NIN={} for jobName={}. Error={}",
//                            nin, jobName, exception.getMessage(), exception);
//                    return null;
//                }
//            }, executorService));
//        }
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//        List<Map<String, Object>> pageResults = new ArrayList<>();
//
//        for (CompletableFuture<Map<String, Object>> future : futures) {
//            Map<String, Object> result = future.join();
//
//            if (result != null) {
//                pageResults.add(result);
//            }
//        }
//
//        if (!pageResults.isEmpty()) {
//            genericEntityRepository.insertAllRows(entityDefinition, pageResults);
//            logger.info("Saved {} row(s) for jobName={} in page={}",
//                    pageResults.size(), jobName, pageNumber);
//        }
//    }

    /**
     * Maps API response values to entity fields.
     *
     * @param entityDefinition entity definition that contains field mapping
     * @param response API response body
     * @return mapped row
     */
    private Map<String, Object> toEntityRow(EntityDefinition entityDefinition,
                                            Map<String, Object> response) {
        Map<String, Object> row = new LinkedHashMap<>();

        for (String fieldName : entityDefinition.getFieldMapping().keySet()) {
            row.put(fieldName, response.get(fieldName));
        }

        return row;
    }

    /**
     * Processes all beneficiaries without using a range.
     *
     * @param jobName job name used to load configuration
     */
    public void processAllBeneficiaries(String jobName) {
        validateJobName(jobName);

        EntityDefinition entityDefinition = entityDefinitionRegistry.get(jobName);

        logger.info("Starting full parallel processing for jobName={}", jobName);

        fetchAndProcessAllPages(jobName, entityDefinition);

        logger.info("Completed full parallel processing for jobName={}", jobName);
    }

    /**
     * Processes all beneficiaries page by page.
     * @param jobName job name used for processing
     * @param entityDefinition entity definition for the target table
     */
    private void fetchAndProcessAllPages(String jobName,
                                         EntityDefinition entityDefinition) {
        int pageNumber = 0;

        while (true) {
            Page<BeneficiaryEntity> beneficiaryPage =
                    beneficiaryRepository.findAll(PageRequest.of(pageNumber,
                            databaseChunkSize));

            if (!beneficiaryPage.hasContent()) {
                logger.info("No more beneficiaries found for jobName={}", jobName);
                break;
            }

            processPage(jobName,
                    entityDefinition,
                    beneficiaryPage,
                    pageNumber);
            pageNumber++;
        }
    }

    /**
     * Validates the job name.
     *
     * @param jobName job name to validate
     */
    private void validateJobName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name must not be null or blank");
        }
    }

    /**
     * Validates the start and end range values.
     *
     * @param start range start value
     * @param end range end value
     */
    private void validateRange(Long start, Long end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Range start and end must not be null");
        }

        if (start > end) {
            throw new IllegalArgumentException("Range start must not be greater than end");
        }
    }
    /**
     * Shuts down the executor service before bean destruction.
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        logger.info("RangePaginationProcessorService executor service shutdown completed");
    }
}