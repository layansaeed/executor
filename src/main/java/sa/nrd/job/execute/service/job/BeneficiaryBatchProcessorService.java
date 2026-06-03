package sa.nrd.job.execute.service.job;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import sa.nrd.job.execute.exception.MaxRetryAttemptsReachedException;
import sa.nrd.job.execute.model.beneficiary.BeneficiaryEntity;
import sa.nrd.job.execute.model.manage.EntityDefinition;
import sa.nrd.job.execute.repository.BeneficiaryJpaRepository;
import sa.nrd.job.execute.repository.GenericEntityRepository;
import sa.nrd.job.execute.service.bean.EntityDefinitionRegistry;
import sa.nrd.job.execute.service.integration.DynamicCallService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
     * Processes all beneficiaries within a specific NIN row range.
     *
     * @param jobName job name used for processing
     * @param start range start
     * @param end range end
     */
    public void processByRange(String jobName,
                               Long start,
                               Long end) {
        validateJobName(jobName);
        validateRange(start, end);

        EntityDefinition entityDefinition = entityDefinitionRegistry.get(jobName);

        logger.info("Starting parallel processing for jobName={} rangeStart={} rangeEnd={}",
                jobName,
                start,
                end);

        long currentRangeStart = start;

        while (currentRangeStart <= end) {
            long currentRangeEnd = currentRangeStart + rangeChunkSize - 1;

            if (currentRangeEnd > end) {
                currentRangeEnd = end;
            }

            processChunk(
                    jobName,
                    entityDefinition,
                    currentRangeStart,
                    currentRangeEnd
            );

            currentRangeStart = currentRangeEnd + 1;
        }

        logger.info("Completed parallel processing for jobName={} rangeStart={} rangeEnd={}",
                jobName,
                start,
                end);
    }

    /**
     * Processes one range window page by page.
     *
     * @param jobName job name used for processing
     * @param entityDefinition entity definition for target table
     * @param rangeStart range window start
     * @param rangeEnd range window end
     */
    private void processChunk(String jobName,
                              EntityDefinition entityDefinition,
                              Long rangeStart,
                              Long rangeEnd) {

        int pageNumber = 0;

        logger.info("Processing range window {} - {} for jobName={}",
                rangeStart,
                rangeEnd,
                jobName);

        while (true) {
            Page<BeneficiaryEntity> beneficiaryPage =
                    beneficiaryRepository.findBeneficiaries(
                            PageRequest.of(pageNumber, databaseChunkSize),
                            rangeStart,
                            rangeEnd
                    );

            if (!beneficiaryPage.hasContent()) {
                logger.info("No more beneficiaries in range window {} - {} for jobName={}",
                        rangeStart,
                        rangeEnd,
                        jobName);
                break;
            }

            processPage(
                    jobName,
                    entityDefinition,
                    beneficiaryPage,
                    pageNumber
            );

            pageNumber++;
        }
    }

    /**
     * Processes one DB page.
     *
     * One CompletableFuture handles the full page list of NINs.
     * If max retry attempts are reached, the current page failure rows are saved first,
     * then the exception is thrown again to stop the whole job.
     *
     * @param jobName job name used for processing
     * @param entityDefinition entity definition for the target table
     * @param beneficiaryPage current beneficiary page
     * @param pageNumber current page number
     */
    private void processPage(String jobName,
                             EntityDefinition entityDefinition,
                             Page<BeneficiaryEntity> beneficiaryPage,
                             int pageNumber) {

        logger.info("Processing page {} for jobName={} with {} beneficiary record(s)",
                pageNumber,
                jobName,
                beneficiaryPage.getNumberOfElements());

        //gets all NINs from the page
        List<Long> nins = getNins(beneficiaryPage);

        CompletableFuture<List<Map<String, Object>>> pageFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        List<Map<String, Object>> responses =
                                dynamicCallService.callApis(jobName, nins);

                        return toEntityRows(entityDefinition, responses);

                    } catch (MaxRetryAttemptsReachedException exception) {
                        // get failed responses from exception
                        List<Map<String, Object>> failedRows =
                                toEntityRows(entityDefinition, exception.getResponses());

                        //    map them to DB rows
                        //    save them into DB (save current page)
                        saveRows(jobName, entityDefinition, pageNumber, failedRows);

                        logger.warn("Max retry attempts reached for jobName={} in page={}. Saved {} failed row(s), stopping job.",
                                jobName,
                                pageNumber,
                                failedRows.size());

                        //throw exception again to stop the whole job
                        //then kill the job, do not continue next page & do not continue next range
                        throw exception;

                    } catch (Exception exception) {
                       // if (exception instanceof MaxRetryAttemptsReachedException maxRetryException) {}
                        logger.error("Failed processing page={} for jobName={}. Error={}",
                                pageNumber,
                                jobName,
                                exception.getMessage(),
                                exception);

                        return new ArrayList<>();
                    }
                }, executorService);
        //After creating the CompletableFuture
        //normal case:
        //callApis returns responses
        //responses mapped to rows
        //join returns pageResults
        //saveRows saves normal page rows

        //Max retry case:
        //callApis throws MaxRetryAttemptsReachedException
        //catch saves failed rows
        //catch throws exception again
        //pageFuture.join() stops
        //outer job stops
        List<Map<String, Object>> pageResults = pageFuture.join();

        saveRows(jobName, entityDefinition, pageNumber, pageResults);
    }

    /**
     * Processes all beneficiaries without range filtering.
     *
     * @param jobName job name used for processing
     */
    public void processAllBeneficiaries(String jobName) {
        validateJobName(jobName);

        EntityDefinition entityDefinition = entityDefinitionRegistry.get(jobName);

        logger.info("Starting full parallel processing for jobName={}", jobName);

        fetchAndProcessAllPages(jobName, entityDefinition);

        logger.info("Completed full parallel processing for jobName={}", jobName);
    }

    /**
     * Fetches and processes all beneficiaries page by page.
     *
     * @param jobName job name used for processing
     * @param entityDefinition entity definition for target table
     */
    private void fetchAndProcessAllPages(String jobName,
                                         EntityDefinition entityDefinition) {
        int pageNumber = 0;

        while (true) {
            Page<BeneficiaryEntity> beneficiaryPage =
                    beneficiaryRepository.findAll(
                            PageRequest.of(pageNumber, databaseChunkSize)
                    );

            if (!beneficiaryPage.hasContent()) {
                logger.info("No more beneficiaries found for jobName={}", jobName);
                break;
            }

            processPage(
                    jobName,
                    entityDefinition,
                    beneficiaryPage,
                    pageNumber
            );

            pageNumber++;
        }
    }

    /**
     * Extracts NIN values from the current page.
     *
     * @param beneficiaryPage current page
     * @return list of NINs
     */
    private List<Long> getNins(Page<BeneficiaryEntity> beneficiaryPage) {
        List<Long> nins = new ArrayList<>();

        for (BeneficiaryEntity beneficiary : beneficiaryPage.getContent()) {
            nins.add(beneficiary.getNin());
        }

        return nins;
    }

    /**
     * Converts API responses to entity rows.
     *
     * @param entityDefinition entity definition
     * @param responses API responses
     * @return mapped rows
     */
    private List<Map<String, Object>> toEntityRows(EntityDefinition entityDefinition,
                                                   List<Map<String, Object>> responses) {
        List<Map<String, Object>> rows = new ArrayList<>();

        if (responses == null || responses.isEmpty()) {
            return rows;
        }

        for (Map<String, Object> response : responses) {
            rows.add(toEntityRow(entityDefinition, response));
        }

        return rows;
    }

    /**
     * Maps one API response to one DB row based on XML field mapping.
     *
     * @param entityDefinition entity definition
     * @param response API response
     * @return mapped DB row
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
     * Saves rows to the target table if rows exist.
     *
     * @param jobName job name used for logging
     * @param entityDefinition entity definition
     * @param pageNumber current page number
     * @param rows rows to save
     */
    private void saveRows(String jobName,
                          EntityDefinition entityDefinition,
                          int pageNumber,
                          List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        genericEntityRepository.insertAllRows(entityDefinition, rows);

        logger.info("Saved {} row(s) for jobName={} in page={}",
                rows.size(),
                jobName,
                pageNumber);
    }

    /**
     * Validates job name.
     *
     * @param jobName job name
     */
    private void validateJobName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name must not be null or blank");
        }
    }

    /**
     * Validates range values.
     *
     * @param start range start
     * @param end range end
     */
    private void validateRange(Long start,
                               Long end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Range start and end must not be null");
        }

        if (start > end) {
            throw new IllegalArgumentException("Range start must not be greater than end");
        }
    }

    /**
     * Shuts down executor service.
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        logger.info("BeneficiaryBatchProcessorService executor service shutdown completed");
    }
}