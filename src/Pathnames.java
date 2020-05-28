import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class Pathnames {
    public static String runFileLocation = "/home/glbrooks/BETTER/runfiles/";
    public static String taskFileLocation = "/home/glbrooks/BETTER/taskfiles/";
    public static String queryFileLocation = "/home/glbrooks/BETTER/queryfiles/";
    public static String indexLocation = "/home/glbrooks/BETTER/indexes/BETTER-DryRun-v3";
    public static String galagoLocation = "/home/glbrooks/BETTER/galago/lemur-galago/core/target/appassembler/bin/";
    public static String qrelFileLocation = "/home/glbrooks/BETTER/qrelfiles/";
    private static InetAddress ip;
    private static String hostname;

    static {
        try {
            ip = InetAddress.getLocalHost();
            hostname = ip.getHostName();
//            System.out.println("Your current Hostname : " + hostname);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        getPathnames();
    }
    public static void getPathnames() {
        try {
            ip = InetAddress.getLocalHost();
            hostname = ip.getHostName();
//            System.out.println("Your current Hostname : " + hostname);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String configfilename = (hostname + ".config.properties").toLowerCase();
        try (
                InputStream input = Pathnames.class.getClassLoader().getResourceAsStream(configfilename)) {
            Properties prop = new Properties();
            prop.load(input);

            runFileLocation = prop.getProperty("runfile.location");
            taskFileLocation = prop.getProperty("taskfile.location");
            queryFileLocation = prop.getProperty("queryfile.location");
            indexLocation = prop.getProperty("index.location");
            galagoLocation = prop.getProperty("galago.location");
            qrelFileLocation = prop.getProperty("qrelfile.location");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
