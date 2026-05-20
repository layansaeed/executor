package sa.nrd.job.execute.model.manage;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "JOB_DETAILS", schema = "EXT")
@Data
public class JobDetails {

    @Id
    @Column(name = "JOB_NAME")
    private String jobName;
}