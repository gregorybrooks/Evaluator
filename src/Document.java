import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Document {
    /**
     * Gets the text of a document, using Galago's doc function.
     * @param docid The external ID of the document to fetch (with dashes).
     * @return Returns the text of the document.
     * @throws IOException
     * @throws InterruptedException
     */
    public static String getDocument (String docid) throws IOException, InterruptedException {
//        String command = Pathnames.galagoLocation +
//                "galago doc --index=" + Pathnames.indexLocation + " --id=" + docid;
        String command = "galago";
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            command = "galago.bat";
        }
//        System.out.println(command);  // TEMP
//        System.out.println(System.getProperty("os.name")); // TEMP
        ProcessBuilder processBuilder = new ProcessBuilder(
                Pathnames.galagoLocation + command, "doc",
                "--index=" + Pathnames.indexLocation,
                "--id=" + docid);
//        if (WINDOWS) {
//            processBuilder.command("cmd.exe", "/c", command);
//        }
//        else {
//            processBuilder.command("bash", "-c", command);
//        }
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String docText = "<b>DOCUMENT: </b>";
        String line;
        while ((line = reader.readLine()) != null) {
            if ((line.length() == 0)
                    || (line.equals("Text :<TEXT>"))
                    || (line.equals("</TEXT>"))
                    || (line.startsWith("Metadata: "))
                    || (line.startsWith("Identifier: "))) {
                continue;
            }
            docText += (line + System.lineSeparator());
        }
        int exitVal = process.waitFor();
        if (exitVal != 0) {
            System.out.println("Unexpected ERROR while executing Galago. Exit value is " + exitVal);
        }
        String regexTarget = "^Identifier.*<TEXT>";
        docText = docText.replaceAll(regexTarget,"");
        regexTarget = "\n";
        docText = docText.replaceAll(regexTarget,"<br>");
        return docText;
    }
}
