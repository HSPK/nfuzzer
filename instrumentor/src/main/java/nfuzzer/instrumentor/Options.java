package nfuzzer.instrumentor;

import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.kohsuke.args4j.Option;

// 用于命令行参数解析、为nfuzzer.instrumentor.Instrumentor提供操作函数的类
public class Options {

	@Option(name = "-i", usage = "Specify input file/dir")
	private String input;
	private HashSet<String> inputClasses;
	
	public String getRawInput() {
		return input;
	}
	
	public HashSet<String> getInput() throws Exception {
		if (inputClasses == null) {
			inputClasses = new HashSet<>();
			if (input.endsWith(".class")) {
				// 单个类文件，必须是 classpath 上目录的相对路径
				inputClasses.add(input);
			} else {
				// 目录
				recordLog.writeLog("Loading dir: " + input);
				//System.out.println("Loading dir: " + input);
				loadDirectory(input, inputClasses);
				addToClassPath(input);
			}
		}
		return inputClasses;
	}

	private static void addToClassPath(String url) {
		try {
			File file = new File(url);
			Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
			method.setAccessible(true);
		    method.invoke(ClassLoader.getSystemClassLoader(), new Object[]{file.toURI().toURL()});
		} catch (Exception e) {
			throw new RuntimeException("Error adding location to class path: " + url);
		}
	    
	}

	private void loadDirectory(String input, HashSet<String> inputClasses) {
		final int dirprefix;
		if (input.endsWith("/"))
			dirprefix = input.length();
		else
			dirprefix = input.length()+1;
		
		try {
			Files.walk(Paths.get(input)).filter(Files::isRegularFile).forEach(filePath -> {
				String name = filePath.toString();
				try {
					recordLog.writeLog("Found file " + name);
				} catch (Exception e) {
					e.printStackTrace();
				}
				//System.out.println("Found file " + name);
				if (name.endsWith(".class")) {
					inputClasses.add(name.substring(dirprefix));
				}

			});
		} catch (IOException e) {
			throw new RuntimeException("Error reading from directory: " + input);
		}
	}
	
	@Option(name = "-o", usage = "Specificy output file/dir")
	private String output;
	
	public String getOutput() {
		return output;
	}

	// 共享内存文件路径
	@Option(name = "-sp", usage = "shm path")
	private String sp;

	public String getSp() {
		return sp;
	}

	// socket通信端口
	@Option(name = "-cp", usage = "cov port")
	private Integer cp;

	public Integer getCp() {
		return cp;
	}

	// 单例
	private static Options options;

	public static void resetInstance() {
		options = null;
	}

	public static Options v() {
		if (null == options) {
			options = new Options();
		}
		return options;
	}

	private Options() {
	}

}

