package sa.nrd.job.execute.exception;
public class MaxRetryAttemptsReachedException extends RuntimeException {
    public MaxRetryAttemptsReachedException(String message) {
        super(message);
    }
}