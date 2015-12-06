/*
 * Copyright 2015, aVineas IT Consulting
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package framework.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * Runner for an OSGi framework. Conforms to the standard framework API and as such is independent of the OSGi framework
 * used. Is a main method that can be called with the following parameters:
 * <ul>
 * <li>-d directory. The directory to scan for .jar files. All jar files in or below this directory are automatically handled
 * as bundles.</li>
 * <li>-p property. An OSGi property that is set to the framework. Properties take the format key=value, like "org.osgi.framework.bootdelegation=*".
 * </li>
 * </ul>
 * Multiple combinations of -d and -p values can be combined.
 */
public class Runner {
	private Framework framework;
	
	private Runner(Framework fw) {
		this.framework = fw;
	} 
	
	/**
	 * Wait for termination. This is either done by a kill-like command or otherwise interrupting the handling. As such
	 * a shutdown hook is installed to make sure that any clean-up is done.
	 */
	private void waitForTermination() {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				handleTermination();
			}
		}));
		try {	
			framework.waitForStop(0L);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}

	void handleTermination() {
		try {
			framework.stop();
		} catch (Exception exc) {
			exc.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Parse a directory (recursive) for files that may be a bundle. This implies that their extension must be .jar, the
	 * directory must be readable and existing.
	 * 
	 * @param dir The directory to parse (recursive). Doesn't need to exist.
	 * @param files The list where the files are collected
	 */
	private static void parseFiles(File dir, List<File> files) {
		if (dir.exists() && dir.isDirectory() && dir.canRead()) {
			File[] contents = dir.listFiles();
			if (contents == null) return;
			for (File f : contents) {
				if (f.isDirectory()) {
					parseFiles(f, files);
				}
				else if (f.getName().endsWith(".jar") || f.getName().endsWith(".bar")) {
					files.add(f);
				}
			}
		}
	}
	
	/**
	 * Main runner. See class description.
	 * 
	 * @param args The arguments passed
	 * @throws Exception In case of severe errors
	 */
	public static void main(String[] args) throws Exception {
		ServiceLoader<FrameworkFactory> sl =
				ServiceLoader.load(FrameworkFactory.class);
		if (!sl.iterator().hasNext()) {
			return;
		}
		Map<String, String> props = new HashMap<>();
		int cnt = 0;
		List<File> files = new ArrayList<>();
		int verbose = 0;
		while (cnt < args.length) {
			// Options: -p means add properties from next argument
			if ("-p".equals(args[cnt])) {
				String value = args[++cnt];
				String[] values = value.split("=");
				props.put(values[0].trim(), (values.length == 1 || values[1] == null) ? "" : values[1].trim());
			}
			else if ("-d".equals(args[cnt])) {
				parseFiles(new File(args[++cnt]), files);
			}
			else if ("-v".equals(args[cnt])) {
				verbose++;
				cnt++;
			}
			else {
				cnt++;
			}
		}
		// Post-process the bundle files. Remove any duplicate file names.
		Map<String, File> unique = new LinkedHashMap<>();
		for (File f : files) {
			unique.put(f.getName(), f);
		}
		FrameworkFactory factory = sl.iterator().next();
		Framework framework = factory.newFramework(props);
		framework.init();
		framework.start();
		BundleContext context = framework.getBundleContext();
		// Install the bundles.
		List<Bundle> bundles = new ArrayList<>();
		for (File file : unique.values()) {
			Bundle b = context.installBundle("file:" + file.getAbsolutePath());
			if (b.getSymbolicName() == null) {
				if (verbose > 1) {
					System.out.println(file + " is not a bundle. Removing bundle id: " + b.getBundleId());
				}
				b.uninstall();
			}
			else {
				if (verbose > 1) {
					System.out.println("File: " + file.getName() + ". Bundle: " + b.getSymbolicName() + ", version: " + 
							b.getVersion() + " installed. Bundle id: " + b.getBundleId());
				}
				bundles.add(b);
			}
		}
		// Resolve them.
		FrameworkWiring wiring = framework.adapt(FrameworkWiring.class);
		wiring.refreshBundles(bundles);
		for (Bundle b : bundles) {
			if (b.getHeaders().get("Fragment-Host") == null) {
				if (verbose > 2) {
					System.out.println("Starting bundle: " + b);
				}
				b.start();
				if (verbose > 0) {
					System.out.println("Bundle: " + b.getBundleId() + " started");
				}
			}
		}
		new Runner(framework).waitForTermination();
	}
}
