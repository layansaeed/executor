package sa.nrd.job.execute.service.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sa.nrd.job.execute.constant.ExecutionStatus;
import sa.nrd.job.execute.dto.JobExecutorBatchRequest;
import sa.nrd.job.execute.dto.JobExecutorRequest;
import sa.nrd.job.execute.job.BeanJob;
import sa.nrd.job.execute.model.manage.JobExecutionEntity;
import sa.nrd.job.execute.repository.JobExecutionJpaRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobExecutionService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final BeanJob beanJob;
    private final JobExecutionJpaRepository jobExecutionRepository;
    private final JobAuditService jobAuditService;
    private final BeneficiaryBatchProcessorService beneficiaryBatchProcessorService;
    private final ManagerCallbackClient managerCallbackClient;

    public JobExecutionService(BeanJob beanJob,
                               JobExecutionJpaRepository jobExecutionRepository,
                               JobAuditService jobAuditService,
                               BeneficiaryBatchProcessorService beneficiaryBatchProcessorService, ManagerCallbackClient managerCallbackClient) {
        this.beanJob = beanJob;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobAuditService = jobAuditService;
        this.beneficiaryBatchProcessorService = beneficiaryBatchProcessorService;
        this.managerCallbackClient = managerCallbackClient;
    }

    /**
     * Runs multiple jobs using execution ids from the batch request.
     *
     * @param request batch request containing execution ids
     */
    public void runJobsByExecutionIds(JobExecutorBatchRequest request) {
        beanJob.markJobStarted();

        try {
            List<JobExecutionEntity> executions = loadExecutions(request);
            executeJobs(executions);

        } finally {
            beanJob.markJobFinished();
        }
    }

    /**
     * Runs one job using one execution id.
     *
     * @param executionId execution identifier
     */
    public void runJobByExecutionId(Long executionId) {
        logger.info("START runJobByExecutionId executionId={}", executionId);
        beanJob.markJobStarted();

        try {
            JobExecutionEntity execution = loadExecution(executionId);

            logger.info("Execution loaded. executionId={}, auditId={}",
                    execution.getExecutionId(),
                    execution.getAuditId());

            executeJob(execution);

        } finally {
            beanJob.markJobFinished();
            managerCallbackClient.notifyExecutionFinished(executionId);
        }
    }
//    public void runJobByExecutionId(Long executionId) {
//        beanJob.markJobStarted();
//
//        try {
//            JobExecutionEntity execution = loadExecution(executionId);
//            executeJob(execution);
//
//        } finally {
//            beanJob.markJobFinished();
//            managerCallbackClient.notifyExecutionFinished(executionId);
//        }
//    }

    /**
     * Loads execution entities from the batch request.
     *
     * @param request batch request
     * @return list of execution entities
     */
    private List<JobExecutionEntity> loadExecutions(JobExecutorBatchRequest request) {
        if (request == null || request.getJobs() == null || request.getJobs().isEmpty()) {
            throw new IllegalArgumentException("Job request must not be empty");
        }

        List<JobExecutionEntity> executions = new ArrayList<>();

        for (JobExecutorRequest executionRequest : request.getJobs()) {
            executions.add(loadExecution(executionRequest.getExecutionId()));
        }

        return executions;
    }

    /**
     * Loads one execution entity by execution id.
     *
     * @param executionId execution identifier
     * @return execution entity
     */
    private JobExecutionEntity loadExecution(Long executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("Execution id must not be null");
        }

        logger.info("Loading execution id={}", executionId);

        return jobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found for id: " + executionId));
    }

    /**
     * Executes all loaded job executions.
     *
     * @param executions list of execution entities
     */
    private void executeJobs(List<JobExecutionEntity> executions) {
        for (JobExecutionEntity execution : executions) {
            executeJob(execution);
        }
    }

    /**
     * Executes one job execution and updates audit status.
     *
     * @param execution execution entity
     */
    private void executeJob(JobExecutionEntity execution) {
        Long executionId = execution.getExecutionId();
        Long auditId = execution.getAuditId();

        if (auditId == null) {
            throw new IllegalStateException("Audit id is null for executionId=" + executionId);
        }


        try {
            String jobName = execution.getJobName();
            Long start = execution.getNinRangeStart();
            Long end = execution.getNinRangeEnd();

            jobAuditService.updateStatus(auditId, ExecutionStatus.PROCESSING);

            logger.info("Executing executionId={}, auditId={}, jobName={}, start={}, end={}",
                    executionId, auditId, jobName, start, end);

            beneficiaryBatchProcessorService.processByRange(jobName, start, end);

            jobAuditService.updateStatus(auditId, ExecutionStatus.SUCCEEDED);

        } catch (Exception exception) {
            if (auditId != null) {
                jobAuditService.updateStatus(auditId, ExecutionStatus.FAILED);
            }

            logger.error("Failed executionId={}, auditId={}", executionId, auditId, exception);
            throw exception;
        }
    }

    /**
     * Runs a full job using only the job name.
     *
     * @param jobName job identifier
     */
    public void runFullJobByJobName(String jobName) {
        beanJob.markJobStarted();

        try {
            if (jobName == null || jobName.trim().isEmpty()) {
                throw new IllegalArgumentException("Job name must not be null or empty");
            }

            String trimmedJobName = jobName.trim();

            logger.info("Executing full job for jobName={}", trimmedJobName);

            beneficiaryBatchProcessorService.processAllBeneficiaries(trimmedJobName);

        } catch (Exception exception) {
            logger.error("Failed full job for jobName={}", jobName, exception);
            throw exception;

        } finally {
            beanJob.markJobFinished();
        }
    }
}