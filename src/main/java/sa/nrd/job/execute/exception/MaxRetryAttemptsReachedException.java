//package sa.nrd.job.execute.exception;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//public class MaxRetryAttemptsReachedException extends RuntimeException {
//
//    private final List<Map<String, Object>> responses;
//
//    public MaxRetryAttemptsReachedException(String message,
//                                            List<Map<String, Object>> responses) {
//        super(message);
//        this.responses = responses == null ? new ArrayList<>() : responses;
//    }
//
//    public List<Map<String, Object>> getResponses() {
//        return responses;
//    }
//}