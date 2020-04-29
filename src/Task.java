import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Task {
    private static final Logger logger = Logger.getLogger("ConvertDryRunTasks");
    public String taskNum;
    public String taskTitle;
    public String taskStmt;
    public String taskNarr;
    public String taskInScope;
    public Map<String,Request> requests;
    public List<String> taskDocList;

    /**
     * Contructs a Task from a JSON representation of an analytic task.
     * @param task The JSONObject version of the task.
     */
    Task(JSONObject task) {   // convert from JSON
        this.taskNum = (String) task.get("task-num");
        this.taskTitle = Evaluator.filterCertainCharacters((String) task.get("task-title"));
        this.taskStmt = Evaluator.filterCertainCharacters((String) task.get("task-stmt"));
        this.taskNarr = Evaluator.filterCertainCharacters((String) task.get("task-narr"));
        this.taskInScope = Evaluator.filterCertainCharacters((String) task.get("task-in-scope"));
        JSONArray taskDocs = (JSONArray) task.get("task-docs");
        JSONArray taskRequests = (JSONArray) task.get("requests");

        requests = new HashMap<String,Request>();
        taskDocList = new ArrayList<String>();

        for (Object o : taskRequests) {
            Request r = new Request((JSONObject) o);
            requests.put(r.reqNum,r);
        }
        for (Object d : taskDocs) {
            taskDocList.add((String) d);
        }
    }

    /**
     * Prints this Task's fields to the log.
     */
    public void logTaskData () {
        logger.info("Task Number: " + taskNum);
        logger.info("Task Title: " + taskTitle);
        logger.info("Task Statement: " + taskStmt);
        logger.info("Task Narrative: " + taskNarr);
        logger.info("Task In Scope: " + taskInScope);
        logger.info("Task Docs: " + taskDocList);
    }

    /**
     * Prints this Task's fields to STDOUT.
     */
    public void printTaskData () {
        System.out.println("Task Number: " + taskNum);
        System.out.println("Task Title: " + taskTitle);
        System.out.println("Task Statement: " + taskStmt);
        System.out.println("Task Narrative: " + taskNarr);
        System.out.println("Task In Scope: " + taskInScope);
        System.out.println("Task Docs: " + taskDocList);
    }
}

