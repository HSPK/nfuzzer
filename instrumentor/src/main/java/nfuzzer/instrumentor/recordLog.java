package nfuzzer.instrumentor;

import java.util.logging.*;
import java.util.Date;

public class recordLog {

    public static void writeLog(String log) throws Exception {
        System.setProperty("jdk.internal.FileHandlerLogging.maxLocks", "20000");

        Logger logger = Logger.getLogger("lavas-oft");
        Date now = new Date();
        FileHandler fileHandler = new FileHandler("instrument.log", false);
        fileHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return now + "\n" + record.getLevel() + ": " + record.getMessage() + "\n";
            }
        });
        logger.addHandler(fileHandler);
        logger.info(log);
    }
}
