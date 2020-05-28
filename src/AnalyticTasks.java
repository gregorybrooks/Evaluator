import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

public class AnalyticTasks {
    private static final String taskFilesDirectory = Pathnames.taskFileLocation;
    /**
     * The file that contains the task and request definitions.
     */
    private static final String tasksAndRequestsFile = taskFilesDirectory + "dry-run-topics.auto.json";

    /**
     * The tasks in the tasksAndRequestsFile converted into a Map of Task objects.
     */
    private final Map<String, Task> tasks = new HashMap<>();

    private class RelevanceJudgementKey {
        String requestID;
        String docid;
        RelevanceJudgementKey(String requestID, String docid) {
            this.requestID = requestID;
            this.docid = docid;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelevanceJudgementKey oKey = (RelevanceJudgementKey) o;
            if (!requestID.equals(oKey.requestID)) return false;
            return (docid.equals(oKey.docid));
        }
        @Override
        public int hashCode() {
            int result = requestID.hashCode();
            result = 31 * result + (docid.hashCode());
            return result;
        }
    }

    private static final String qrelFile = Pathnames.qrelFileLocation + "qrel.txt";
    private final Map<RelevanceJudgementKey,RelevanceJudgment> relevanceJudgments =
            new HashMap<RelevanceJudgementKey, RelevanceJudgment>();
    /**
     * Reads the Dry Run JSON file containing the analytic tasks and requests to be processed
     * and constructs a Map of Tasks and Requests that represent them.
     * @throws IOException
     * @throws ParseException
     */
    AnalyticTasks() throws IOException, ParseException {
        /* Get task and request definitions */
        Reader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(tasksAndRequestsFile)));
        JSONParser parser = new JSONParser();
        JSONArray tasksJSON = (JSONArray) parser.parse(reader);
        for (Object oTask : tasksJSON) {
            Task t = new Task((JSONObject) oTask);  // this gets Requests, too
            tasks.put(t.taskNum, t);
        }

        /* Get relevance judgments for these requests */
        File f = new File(qrelFile);
        if (f.exists()) {
            BufferedReader qrelReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(qrelFile)));
            String line = qrelReader.readLine();
            while (line != null) {
                String[] tokens = line.split(",");
                String who = tokens[0];
                String requestID = tokens[1];
                String docid = tokens[2];
                String judgment = tokens[3];
                String when = tokens[4];
                RelevanceJudgment j = new RelevanceJudgment(requestID, docid, who, when, judgment);
                RelevanceJudgementKey jk = new RelevanceJudgementKey(requestID,docid);
                relevanceJudgments.put(jk, j);
                line = qrelReader.readLine();
            }
            qrelReader.close();
        }
    }

    public RelevanceJudgment getRelevanceJudgement(String requestID, String docid) {
        RelevanceJudgementKey jk = new RelevanceJudgementKey(requestID,docid);
        return relevanceJudgments.get(jk);
    }

    public List<String> getRequestRelevantDocids(String requestID) {
        List<String> docids = new ArrayList<>();
        for (Map.Entry<RelevanceJudgementKey,RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgementKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)
                    && j.judgment == RelevanceJudgment.RelevanceJudgmentType.REQUEST_RELEVANT) {
                docids.add(k.docid);
            }
        }
        return docids;
    }

    public List<String> getTaskAndRequestRelevantDocids(String requestID) {
        List<String> docids = new ArrayList<>();
        for (Map.Entry<RelevanceJudgementKey,RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgementKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)
                    && ((j.judgment == RelevanceJudgment.RelevanceJudgmentType.REQUEST_RELEVANT)
                       || (j.judgment == RelevanceJudgment.RelevanceJudgmentType.TASK_RELEVANT))) {
                docids.add(k.docid);
            }
        }
        return docids;
    }

    public List<String> getRequestIDs() {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String,Task> entry : tasks.entrySet()) {
            Task t = entry.getValue();
            for (Map.Entry<String, Request> requestEntry : t.requests.entrySet()) {
                String requestNbr = requestEntry.getKey();
                list.add(requestNbr);
            }
        }
        return list;
    }

    public Map<String,Task> getTasks() { return tasks; }

    public Task findTask(String taskNum) {
        return tasks.get(taskNum);
    }

    public Request findRequest(Task t, String requestNum) {
        return t.requests.get(requestNum);
    }

    public Request findRequest(String requestID) {
        Task t = findTask(requestID.substring(0, 5));
        return t.requests.get(requestID);
    }

    public String getTaskInfo (String requestID) {
        String taskID = requestID.substring(0,5);
        Task t = findTask(taskID);
        if (t == null)
            return "Task " + taskID + " not found";
        return t.getTaskData();
    }

    public String getRequestInfo (String requestID) {
        String taskID = requestID.substring(0,5);
        Task t = findTask(taskID);
        if (t == null)
            return "Request " + requestID + " task not found";
        Request r = findRequest(t, requestID);
        if (r == null)
            return "Request " + requestID + " not found";
        return r.getRequestData();
    }

    /**
     * Force the taskDocList field to contain all the docs in
     * all of that Task's Requests.
     * We do this so we have a handy list of all the hint docs at task or
     * request level for the "E2" evaluation approach.
     */
    public void fixTaskDocs() {
        tasks.forEach((k,v)->{
            String taskName = k;
            Task t = v;
            t.requests.forEach((rk,rv)->{
                String requestName = rk;
                Request r = rv;
                r.reqDocList.forEach((d)->{
                    if (!t.taskDocList.contains(d)) {
                        t.taskDocList.add(d);
                    }
                });
            });
        });
    }


}
