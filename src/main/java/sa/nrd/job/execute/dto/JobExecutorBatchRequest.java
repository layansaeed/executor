package sa.nrd.job.execute.dto;


import lombok.Data;

import java.util.List;

@Data
public class JobExecutorBatchRequest {
  private   List<JobExecutorRequest> jobs ;
}
