/*
 * Copyright 2009-2010 aVineas IT Consulting
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
package org.avineas.log4j;

import java.io.File;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.DOMConfigurator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

/**
 * Bundle activator for this log4j bundle. The activator listens for
 * configuration changes and when an update is received, re-configures
 * log4j with the new configuration.
 *
 * @author Arie van Wijngaarden
 */
public class Activator implements BundleActivator, ManagedService {
    private static final String PID = "log4j.configuration";
    private static final String FILE = "file";
    private Dictionary<String, String> properties;
    private ServiceRegistration<?> registration;

	@Override
	public void start(BundleContext context) {
	    properties = new Hashtable<String, String>();
	    properties.put(Constants.SERVICE_PID, PID);
	    String path = context.getProperty(PID);
	    if (path == null) {
	        path = "configuration/log4j.xml";
	    }
	    properties.put(FILE, path);
	    registration = context.registerService(ManagedService.class.getName(),
	        this, properties);
	}

    @SuppressWarnings("unchecked")
    @Override
    public synchronized void updated(@SuppressWarnings("rawtypes") Dictionary args) {
        if (args != null) {
            properties = args;
            properties.put(Constants.SERVICE_PID, PID);
        }
        String file = properties.get(FILE);
        if (file != null && new File(file).canRead()) {
            LogManager.resetConfiguration();
            if (file.endsWith(".properties")) {
                // Load as property file.
                PropertyConfigurator.configure(file);
            }
            else {
                // Load the file as DOM file.
                DOMConfigurator.configure(file);
            }
        }
    }

	@Override
	public void stop(BundleContext context) {
	    // Strictly spoken not needed.
	    registration.unregister();
	}
}