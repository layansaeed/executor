package sa.nrd.job.execute.controller;

import sa.nrd.job.execute.job.BeanJob;
import sa.nrd.job.execute.dto.JobExecutorBatchRequest;
import sa.nrd.job.execute.dto.JobExecutorRequest;
import sa.nrd.job.execute.service.job.JobExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/v1/job-executor")
public class JobController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());
    private final JobExecutionService jobExecutionService;
    private  final BeanJob beanJob;
    public JobController(JobExecutionService jobExecutionService, BeanJob beanJob) {
        this.jobExecutionService = jobExecutionService;
        this.beanJob = beanJob;
    }

@PostMapping()
public ResponseEntity<?> runJobs(@RequestBody JobExecutorBatchRequest request) {
    try {
        System.out.println("job list size: " + request.getJobs().size());
        List<JobExecutorRequest> jobs = request.getJobs();
        for(JobExecutorRequest job:jobs){
            System.out.println("execution id= " + job.getExecutionId());
        }
        jobExecutionService.runJobsByExecutionIds(request);
        return ResponseEntity.ok().build();
    } catch (Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}

//    @PostMapping()
//    public ResponseEntity<?> runJob(@RequestBody JobExecutorRequest request) {
//        try {
//            System.out.println("execution id= " + request.getExecutionId());
//
//            jobExecutionService.runJobsByExecutionId(request);
//            return ResponseEntity.ok().build();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            return ResponseEntity.badRequest().body(ex.getMessage());
//        }
//    }

    /**
     * Executes one job using executionId from path variable.
     */
    @PostMapping("/{executionId}")
    public ResponseEntity<?> runJob(@PathVariable Long executionId) {
        logger.info("Executor received executionId={}", executionId);
        try {
            jobExecutionService.runJobByExecutionId(executionId);
            return ResponseEntity.ok().build();

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

        @GetMapping("/alive")
        public ResponseEntity<?> alive() {
            logger.info(" alive");
//        String ip = request.getRemoteAddr();
//        int port = request.getRemotePort();
            logger.info("Alive check called ");
            return ResponseEntity.ok().build();
        }

    @GetMapping("/load")
    public ResponseEntity<?> load() {
        logger.info("load");
        beanJob.reloadBeans();
        return ResponseEntity.ok().build();
    }

    /**
     * Executes one full job using jobName from path variable.
     */
    @PostMapping("/run/{jobName}")
    public ResponseEntity<?> runFullJob(@PathVariable String jobName) {
        try {
            jobExecutionService.runFullJobByJobName(jobName);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

}