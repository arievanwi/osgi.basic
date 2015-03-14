/*
 * Copyright 2009-2010, FUJIFILM Manufacturing Europe B.V.
 * Copyright 2009-2014, aVineas IT Consulting
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Activator for this bundle. Takes care of creating a new file handlers and
 * handling the properties related to it.
 * 
 * @author Arie van Wijngaarden
 */
public class Activator implements BundleActivator {
    private static final String LOCPROPERTY = "cm.location";
    private Timer timer;
    private ServiceTracker<Object, Object> tracker;

    @Override
    public void start(final BundleContext context) {
        String dir = context.getProperty(LOCPROPERTY);
        if (dir == null)
            return;
        Map<String, PropertyFileHandler> persisters = new HashMap<String, PropertyFileHandler>();
        File file = new File(dir);
        if (!file.isDirectory()) {
            if (PropertyFileHandler.filter().accept(file)) {
                persisters.put(file.toString(), new PropertyFileHandler(file));
            }
        } else {
            for (File entry : file.listFiles()) {
                if (entry.isDirectory())
                    continue;
                if (PropertyFileHandler.filter().accept(entry))
                    persisters.put(entry.toString(), new PropertyFileHandler(
                            entry));
            }
        }
        // We got them all.
        final Collection<? extends ConfigurationSynchronizer> backers = persisters
                .values();
        // Do the tracking.
        tracker = new ServiceTracker<Object, Object>(context, ConfigurationAdmin.class.getName(),
            new ServiceTrackerCustomizer<Object, Object>() {
                @Override
                public void removedService(ServiceReference<Object> ref,
                        Object admin) {
                    context.ungetService(ref);
                    update(null, backers);
                }
    
                @Override
                public void modifiedService(ServiceReference<Object> ref,
                        Object admin) {
                }
    
                @Override
                public Object addingService(ServiceReference<Object> ref) {
                    Object obj = context.getService(ref);
                    update((ConfigurationAdmin) obj, backers);
                    return obj;
                }
            });
        tracker.open();
        // Set the timer.
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void run() {
                update((ConfigurationAdmin) tracker.getService(), backers);
            }
        }, 10000L, 10000L);
    }

    static void update(ConfigurationAdmin admin,
            Collection<? extends ConfigurationSynchronizer> backers) {
        try {
            for (ConfigurationSynchronizer s : backers) {
                s.checkConfiguration(admin);
            }
        } catch (Exception exc) {
        }
    }

    @Override
    public void stop(BundleContext context) {
        timer.cancel();
        tracker.close();
    }
}
