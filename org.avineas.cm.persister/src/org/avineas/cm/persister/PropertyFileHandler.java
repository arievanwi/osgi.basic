/*
 * Copyright 2009-2014 aVineas IT Consulting. All rights reserved.
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
package org.avineas.cm.persister;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Handler of a property file. Is able to read a property file containing
 * persisted configuration admin data.
 */
public class PropertyFileHandler implements ConfigurationSynchronizer {
    private static final String PERSISTENCEPID = "$$key$$";
    // The PID-property separator
    private static final String SEP = "..";
    // Extension of files in the directory
    private static final String EXTENSION = ".properties";
    private File file;
    private ConfigurationAdmin admin;
    private long lastRead;

    PropertyFileHandler(File file) {
        this.file = file;
    }

    /**
     * Return a filter that indicates whether a specific file is acceptable for
     * using as a property persister. This means that the extension of the file
     * must be .properties and the file must both be readable and writable.
     */
    static FileFilter filter() {
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(EXTENSION)
                        && pathname.canRead() && pathname.canWrite();
            }
        };
        return filter;
    }

    /**
     * Get the base persistence identifier for this persister. The information
     * is extracted from the file name.
     * 
     * @return A string giving the base persistence information. May be an empty
     *         string
     */
    private String getBasePid() {
        String name = file.getName();
        int index = name.indexOf(EXTENSION);
        name = name.substring(0, index);
        return name;
    }

    /**
     * Load the properties from the backing store. This means that the file is
     * processed and the information is loaded into a map with dictionaries.
     * 
     * @return A map with dictionaries. Every entry is a pid with its associated
     * dictionary
     */
    private synchronized Map<String, Dictionary<String, Object>> loadProperties() {
        Map<String, Dictionary<String, Object>> map = new HashMap<String, Dictionary<String, Object>>();
        if (!file.exists())
            return map;
        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            props.load(in);
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                // Assume that when the property is
                // hallo.boppers.this.is..me = testje
                // then the pid is "hallo.boppers.this.is" and the
                // entry for the dictionary is "me" -> "testje".
                String key = entry.getKey().toString();
                String value = entry.getValue().toString();
                // Check if the pid is in the property or
                // whether the base name must be used.
                int index = key.lastIndexOf(SEP);
                String pid;
                String prop;
                if (index <= 0) {
                    pid = getBasePid();
                    prop = key;
                } else {
                    pid = key.substring(0, index);
                    prop = key.substring(index + SEP.length());
                }
                if (prop.length() == 0)
                    continue;
                Dictionary<String, Object> dict = map.get(pid);
                if (dict == null) {
                    dict = new Hashtable<String, Object>();
                    map.put(pid, dict);
                }
                String[] values = value.split(",");
                if (values.length > 1) {
                    dict.put(prop, values);
                } else {
                    dict.put(prop, value);
                }
            }
        } catch (Exception exc) {
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (Exception exc) {
                }
        }
        return map;
    }

    private synchronized void update(ConfigurationAdmin adm) throws IOException {
        if (adm == null)
            return;
        // Load the properties.
        Map<String, Dictionary<String, Object>> properties = loadProperties();
        for (Map.Entry<String, Dictionary<String, Object>> entry : properties
                .entrySet()) {
            Dictionary<String, Object> values = entry.getValue();
            Configuration config;
            String factory = (String) values
                    .get(ConfigurationAdmin.SERVICE_FACTORYPID);
            String localPid = entry.getKey();
            if (factory != null) {
                // Locate the existing pid, if any.
                try {
                    Configuration[] existing = adm.listConfigurations("("
                            + PERSISTENCEPID + "=" + localPid + ")");
                    if (existing != null && existing.length > 0) {
                        config = existing[0];
                    } else {
                        config = adm.createFactoryConfiguration(factory, null);
                    }
                } catch (Exception exc) {
                    throw new IOException(exc);
                }
                entry.getValue().put(PERSISTENCEPID, localPid);
            } else {
                config = adm.getConfiguration(localPid, null);
            }
            config.update(entry.getValue());
        }
    }

    @Override
    public void checkConfiguration(ConfigurationAdmin adm) throws IOException {
        if (this.admin == adm && lastRead >= file.lastModified())
            return;
        update(adm);
        this.admin = adm;
        this.lastRead = System.currentTimeMillis();
    }
}
