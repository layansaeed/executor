package sa.nrd.job.execute.service.job;

import sa.nrd.job.execute.model.manage.JobDetails;
import sa.nrd.job.execute.repository.JobDetailsJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// This tells JUnit to enable Mockito in this test class (@Mock/@InjectMocks)
@ExtendWith(MockitoExtension.class)
class JobDetailsServiceTest {

    // This creates a fake version of the repository -> mock object -> not the real DB repository
    @Mock
    private JobDetailsJpaRepository jobDetailsRepository;

    // This creates the service and injects the mocked repository into it.
    @InjectMocks
    private JobDetailsService jobDetailsService;

    @Test
    void getJobRequired_shouldReturnJobDetails_whenJobExists() {
        // Prepare fake data
        String jobName = "HRSD_DIS_ASS";

        // Create fake returned entity
        JobDetails jobDetails = new JobDetails();
        jobDetails.setJobName(jobName);

        // Tell mock repository what to return
        when(jobDetailsRepository.findById(jobName)).thenReturn(Optional.of(jobDetails));

        // Method that will be tested
        JobDetails result = jobDetailsService.getJobRequired(jobName);

        assertNotNull(result);
        assertEquals(jobName, result.getJobName());
    }

    @Test
    void getJobRequired_shouldThrowException_whenJobDoesNotExist() {
        String jobName = "UNKNOWN_JOB";

        // Tell mock repository to return empty
        when(jobDetailsRepository.findById(jobName)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jobDetailsService.getJobRequired(jobName)
        );

        assertEquals("Job not found for name: UNKNOWN_JOB", exception.getMessage());
    }

    // The remaining red text is only a Mockito warning
    // related to dynamic Java agent loading on Java 21, not a test failure.
}