/*
 * Copyright 2014 aVineas IT Consulting. All rights reserved.
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

import java.io.IOException;

import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Checker for a configuration back-end. Implementations of this interface should check their status
 * and update, according to their status, the configurations in a configuration admin service.
 */
public interface ConfigurationSynchronizer {
    /**
     * Check the configuration(s) handled by this backing handler. The method is called when either
     * the configuration admin service appears or disappears or at regular intervals to perform
     * a polling check on changed configurations. The method must synchronize the configuration
     * admin with the backed configurations.
     * 
     * @param admin The configuration admin. May be null when no configuration admin is
     * present (yet)
     */
    public void checkConfiguration(ConfigurationAdmin admin) throws IOException;
}
