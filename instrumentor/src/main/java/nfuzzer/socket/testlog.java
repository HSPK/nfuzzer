package nfuzzer.socket;

import java.io.IOException;
import java.util.logging.*;
import java.util.Date;

public class testlog { 

        static Logger log = Logger.getLogger("lavasoft"); 
        static Date now = new Date();

        public static void writeLog(String logcontent) throws IOException {
                System.setProperty("jdk.internal.FileHandlerLogging.maxLocks", "20000");
                log.setLevel(Level.INFO);
                FileHandler fileHandler = new FileHandler("./test/test.log");
                fileHandler.setLevel(Level.INFO);
                fileHandler.setFormatter(new Formatter() {
                        @Override
                        public String format(LogRecord record) {
                                return now.toString() + "\n" + record.getLevel() + ":" + record.getMessage() + "\n";
                        }
                });

                log.addHandler(fileHandler);
                log.info(logcontent);
        }
}