import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Represents a specific analytic request within the larger analytic task (@see ConvertDryRunTasks.Task).
 */
public class Request {
    private static final Logger logger = Logger.getLogger("ConvertDryRunTasks");
    public String reqNum;
    public String reqText;
    public List<String> reqDocList;
    public List<String> reqExtrList;

    /**
     * Constructs a Request from a JSON representation of the analytic request.
     * @param request The JSONObject version of the request.
     */
    Request(JSONObject request) {
        reqNum = (String) request.get("req-num");
        reqText = Evaluator.filterCertainCharacters((String) request.get("req-text"));
        JSONArray reqDocs = (JSONArray) request.get("req-docs");
        JSONArray reqExtr = (JSONArray) request.get("req-extr");

        reqDocList = new ArrayList<String>();
        reqExtrList = new ArrayList<String>();
        for (Object d : reqDocs) {
            reqDocList.add((String) d);
        }
        for (Object d : reqExtr) {
            reqExtrList.add(Evaluator.filterCertainCharacters((String) d));
        }
    }

    /**
     * Prints this Request's fields to stdout.
     */
    public void printRequestData() {
        System.out.println("Request Number: " + reqNum);
        System.out.println("Request Text: " + reqText);
        System.out.println("Request Docs: " + reqDocList);
        System.out.println("Request Extractions: " + reqExtrList);
    }
    /**
     * Prints this Request's fields to the log.
     */
    public void logRequestData() {
        logger.info("Request Number: " + reqNum);
        logger.info("Request Text: " + reqText);
        logger.info("Request Docs: " + reqDocList);
        logger.info("Request Extractions: " + reqExtrList);
    }
}
