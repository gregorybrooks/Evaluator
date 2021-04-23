package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.*;
import java.util.logging.Logger;

public class Task {
    private static final Logger logger = Logger.getLogger("BetterQueryBuilderFarsi");
    String taskNum;
    String taskTitle;
    String taskStmt;
    String taskNarr;
    List<Request> requests;

    Task(String taskNum, String taskTitle, String taskStmt, String taskNarr,
         List<Request> requests) {
        this.taskNum = taskNum;
        this.taskTitle = taskTitle;
        this.taskNarr = taskNarr;
        this.requests = requests;
    }

    /**
     * Copy constructor (deep copy)
     * @param otherTask The Task to make a copy of.
     */
    Task(Task otherTask) {
        this.taskNum = new String(otherTask.taskNum);
        this.taskTitle = (otherTask.taskTitle == null ? null : new String(otherTask.taskTitle));
        this.taskStmt = (otherTask.taskStmt == null ? null : new String(otherTask.taskStmt));;
        this.taskNarr = (otherTask.taskNarr == null ? null : new String(otherTask.taskNarr));
        this.requests = new ArrayList<>();
        for(Request r : otherTask.requests) {
            this.requests.add(r);
        }
    }

    public List<Request> getRequests() { return requests; }
}


