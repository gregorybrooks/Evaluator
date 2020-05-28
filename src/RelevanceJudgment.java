import java.awt.dnd.InvalidDnDOperationException;

public class RelevanceJudgment {
    public static enum RelevanceJudgmentType {REQUEST_RELEVANT, TASK_RELEVANT, NOT_RELEVANT}
    String requestID;
    String docid;
    String who;
    String when;
    RelevanceJudgmentType judgment;
    RelevanceJudgment(String requestID, String docid, String who, String when, RelevanceJudgmentType judgment) {
        this.requestID = requestID;
        this.docid = docid;
        this.who = who;
        this.when = when;
        this.judgment = judgment;
    }
    RelevanceJudgment(String requestID, String docid, String who, String when, String judgment) {
        this.requestID = requestID;
        this.docid = docid;
        this.who = who;
        this.when = when;
        String newJudgment;
        switch (judgment) {
            case "1":
                newJudgment = "REQUEST_RELEVANT";
                break;
            case "2":
                newJudgment = "TASK_RELEVANT";
                break;
            case "3":
                newJudgment = "NOT_RELEVANT";
                break;
            default:
                throw new IllegalArgumentException("Invalid relevance judgment string:" + judgment
                        + ". Should be one of REQUEST_RELEVANT, TASK_RELEVANT or NOT_RELEVANT.");
        }
        this.judgment = RelevanceJudgmentType.valueOf(newJudgment);
    }
}
