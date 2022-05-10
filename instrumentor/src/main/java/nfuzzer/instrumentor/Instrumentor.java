package nfuzzer.instrumentor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class Instrumentor {
  // default args
  private static String inputPath;
  private static String outputPath;
  private static final String DEFAULT_OUT_PATH = "ins_out";
  private static String shmPath;
  private static final String DEFAULT_SHM_PATH = "shm";
  private static int covPort;
  private static final int DEFAULT_COV_PORT = 60020;
  public static ClassLoader classloader;

  private static final Logger logger = LogManager.getLogger(Instrumentor.class);

  private static void parseArgs(String[] args) throws ParseException {
    // command line arguments
    Options options = new Options();
    options.addOption("i", true, "input class file/dir");
    options.addOption("o", true, "output class file/dir");
    options.addOption("sp", true, "share memory dir");
    options.addOption("cp", true, "covSend port");
    options.addOption("help", false, "print this message");

    // parse command line parser
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    if (cmd.hasOption("help")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("java Instrument [options]", options);
      System.exit(0);
    }
    if (!cmd.hasOption("i")) {
      logger.error("must have an input path");
      System.exit(-1);
    }
    inputPath = cmd.getOptionValue("i");
    outputPath = cmd.getOptionValue("o", DEFAULT_OUT_PATH);
    shmPath = cmd.getOptionValue("sp", DEFAULT_SHM_PATH);
    covPort = Integer.parseInt(cmd.getOptionValue("cp", String.valueOf(DEFAULT_COV_PORT)));
  }

  private static Set<String> getInputClasses() {
    Set<String> set = new HashSet<>();
    if (inputPath.endsWith(".class")) {
      logger.debug("add to input set: " + inputPath);
      set.add(inputPath);
    } else {
      // walk dirs, add all .class file to set
      try (Stream<Path> entries = Files.walk(Paths.get(inputPath))) {
        entries.filter(Files::isRegularFile).forEach(filePath -> {
          // remove root directory
          String name = filePath.subpath(1, filePath.getNameCount()).toString();
          if (name.endsWith(".class")) {
            set.add(name);
            logger.debug("add to input set: " + name);
          }
        });
      } catch (IOException e) {
        logger.error("io exception: " + e.getMessage());
        System.exit(-1);
      }
      // add inputPath to class path
//      try {
//        File file = new File(inputPath);
//        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
//        method.setAccessible(true);
//        method.invoke(ClassLoader.getSystemClassLoader(), file.toURI().toURL());
//      } catch (Exception e) {
//        logger.error("failed to add " + inputPath + " to class path");
//        System.exit(-1);
//      }
    }
    return set;
  }

  public static void main(String[] args) {
    logger.debug("instrument started...");
    // classloader
    classloader = Thread.currentThread().getContextClassLoader();
    // parse command line arguments
    try {
      parseArgs(args);
    } catch (ParseException e) {
      logger.error("parse error: " + e.getMessage());
      return;
    }
    // 加载全部类文件
    logger.debug("get input classes: " + inputPath);
    Set<String> inputClasses = getInputClasses();

    Set<String> skipped = new HashSet<>();

    for (String cls : inputClasses) {
      logger.debug("instrumenting: " + cls);
      String rootOfClass = Paths.get(inputPath).getName(0).toString();
      // read bytecode from class file
      InputStream bytecode = classloader.getResourceAsStream(Paths.get(rootOfClass, cls).toString());
      if (bytecode == null) {
        logger.error("class file not found: " + cls);
        return;
      }

      // asm class writer
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      ClassTransformer ct = new ClassTransformer(cw, shmPath);

      try {
        ClassReader cr = new ClassReader(bytecode);
        cr.accept(ct, 8);
        byte[] bytes = cw.toByteArray();
        writeBytes(cls, bytes);
      } catch (RuntimeException rte) {
        if (rte.getMessage().contains("JSR/RET")) {
          logger.error("[WARNING] RuntimeException during instrumentation: " + rte.getMessage());
          logger.error("Skipping instrumentation of class " + cls);

          loadAndWriteResource(cls);
          skipped.add(cls);
        } else {
          logger.error("unknown runtimeException: " + rte.getMessage());
          return;
        }
      } catch (IOException e) {
        logger.error("io exception occurred when reading bytecode: " + e.getMessage());
        return;
      } catch (Exception e) {
        logger.error("write class unknown error: " + e.getMessage());
        return;
      }
    }

    String[] resources = {
        "nfuzzer/Nfuzzer.class",
        "nfuzzer/Nfuzzer$ApplicationCall.class",
        "nfuzzer/Nfuzzer$FuzzRequest.class",
        "nfuzzer/Mem.class",
        "nfuzzer/socket/CovSend.class",
        "nfuzzer/socket/CovSendThread.class"};

    for (String resource : resources) {
      loadAndWriteResource(resource);
    }

    if (skipped.size() > 0) {
      logger.error("WARNING!!! Instrumentation of some classes has been skipped.");
    }
  }

  // write bytes to file
  private static void writeBytes(String filename, byte[] bytes) {
    Path filePath = Paths.get(filename);
    Path out = Paths.get(outputPath, filename);
    try {
      Files.createDirectories(out.getParent());
      Files.write(out, bytes);
      logger.debug("write file: " + out);
    } catch (IOException e) {
      logger.error("error writing file: " + out);
    }
  }

  private static void loadAndWriteResource(String resource) {
    InputStream is = classloader.getResourceAsStream(resource);
    if (is == null) {
      logger.error("read resource is null: " + resource);
      return;
    }
    byte[] bytes;
    try {
      bytes = IOUtils.toByteArray(is);
    } catch (IOException e) {
      logger.error("io exception when read bytes: " + e.getMessage());
      return;
    }
    writeBytes(resource, bytes);
  }
}
