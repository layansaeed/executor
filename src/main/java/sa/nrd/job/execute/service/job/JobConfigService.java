package sa.nrd.job.execute.service.job;

import sa.nrd.job.execute.config.Config;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JobConfigService {

    private final Config.PropertySourceConfig propertySourceConfig;

    /**
     * Creates the service with the required property source dependency.
     * @param propertySourceConfig configuration property source
     */
    public JobConfigService(Config.PropertySourceConfig propertySourceConfig) {
        this.propertySourceConfig = propertySourceConfig;
    }

    /**
     * Returns the normalized configuration map for the given job name.
     *
     * @param jobName job name used as the configuration prefix
     * @return normalized configuration map
     */
    public Map<String, String> getConfigMap(String jobName) {
        validateJobName(jobName);

        String propertyPrefix = jobName + ".";
        //String -> key with prefix so not clean map
        Map<String, String> rawProperties = propertySourceConfig.getPropertiesStartingWith(propertyPrefix);

        if (rawProperties.isEmpty()) {
            throw new IllegalArgumentException("No configuration found for job name: " + jobName);
        }

        Map<String, String> normalizedConfig = new LinkedHashMap<>();

        for (Map.Entry<String, String> propertyEntry : rawProperties.entrySet()) {
            String fullKey = propertyEntry.getKey();
            String normalizedKey = fullKey.substring(propertyPrefix.length());

            if (normalizedKey.trim().isEmpty()) {
                continue;
            }

            if (normalizedConfig.containsKey(normalizedKey)) {
                throw new IllegalArgumentException(
                        "Duplicate normalized config key '" + normalizedKey + "' for job name: " + jobName
                );
            }

            normalizedConfig.put(normalizedKey, propertyEntry.getValue());
        }

        //return clean map
        return normalizedConfig;
    }

    /**
     * Validates the job name value.
     *
     * @param jobName job name to validate
     */
    private void validateJobName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name must not be null or blank");
        }
    }
}