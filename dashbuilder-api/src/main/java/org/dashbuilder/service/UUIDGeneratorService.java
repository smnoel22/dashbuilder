/**
 * Copyright (C) 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.service;

import org.dashbuilder.model.dataset.DataSet;
import org.dashbuilder.model.dataset.DataSetMetadata;
import org.dashbuilder.model.dataset.DataSetOperation;
import org.jboss.errai.bus.server.annotations.Remote;

/**
 * Service for obtaining UUIDs
 */
@Remote
public interface UUIDGeneratorService {

    /**
     * Generates a brand new UUID in base-64 and without characters forbidden in URLs (plus sign, equal, slash and ampersand)
     * @return A 22 character length, base-64 and URL-safe string.
     */
    String newUUID();
}
