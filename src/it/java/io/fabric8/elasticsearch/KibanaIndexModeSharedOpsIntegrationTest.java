/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.elasticsearch;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;


public class KibanaIndexModeSharedOpsIntegrationTest extends KibanaIndexModeIntegrationBase {

    @Override
    protected Settings additionalNodeSettings() {
        return Settings.builder()
                .put(KibanaIndexMode.OPENSHIFT_KIBANA_INDEX_MODE, KibanaIndexMode.SHARED_OPS)
                .build();
    }
    
    @Test
    public void testNonAdminUserRetrievingDocuments() throws Exception {
        final String [] projects = {"multi-tenancy-1", "multi-tenancy-2"};
        final String userName = "nonadminuser";
        //artificially seed user's kibanaIndex
        String kibanaIndex = getKibanaIndex(KibanaIndexMode.SHARED_OPS, userName, false);
        givenDocumentIsIndexed(kibanaIndex, "config", OLD_KIBANA_VERSION, "myKibanaIndex");
        givenUserIsNotClusterAdmin(userName);
        givenUserIsAdminForProjects(projects);

        //verify access to unique kibana index
        whenGettingDocument(String.format("%s/config/%s", kibanaIndex, OLD_KIBANA_VERSION));
        assertThatResponseIsSuccessful();

        
        //verify seeded index patterns
        for (String project : projects) {
            whenGettingDocument(String.format("%s/index-pattern/%s", kibanaIndex, formatProjectIndexPattern(project, "uuid")));
            assertThatResponseIsSuccessful();
        }

        //verify access to index pattern for project we dont administer
        whenGettingDocument(String.format("%s/index-pattern/%s", kibanaIndex, formatProjectIndexPattern("multi-tenancy-3", "uuid")));
        assertThatResponseIsNotFound();
        
        //verify search to individual projects
        for (String project : projects) {
            whenGettingDocument(String.format("%s/_count", formatProjectIndexPattern(project, "uuid")));
            assertThatResponseIsSuccessful();
        }
        
        //verify search across multiple projects
        whenSearchingProjects(projects);
        assertThatResponseIsSuccessful();

        //verify search to operations projects
        whenGettingDocument(".operations.*/_count");
        assertThatResponseIsForbidden();

        //verify access to unique kibana index
        //this is a false positive on '.kibana' because we transform to a unique index
        whenGettingDocument(".kibana/config/" + OLD_KIBANA_VERSION);
        assertThatResponseIsSuccessful();
        assertThatMessageEquals("myKibanaIndex");
    }

    @Test
    public void testOperationsUserRetrievingDocuments() throws Exception {
        final String [] projects = {"multi-tenancy-1", "multi-tenancy-2", "multi-tenancy-2"};
        final String userName = "anAdminUser";

        givenUserIsClusterAdmin(userName);
        givenUserIsAdminForProjects(projects);
        
        //verify search to individual projects
        for (String project : projects) {
            whenGettingDocument(String.format("%s/_count", formatProjectIndexPattern(project, "uuid")));
            assertThatResponseIsSuccessful();
        }
        
        //verify search across multiple projects
        whenSearchingProjects(projects);
        assertThatResponseIsSuccessful();
        
        //verify search to operations projects
        whenGettingDocument(".operations.*/_count");
        assertThatResponseIsSuccessful();
        
        //verify access to unique kibana index
        whenGettingDocument(String.format(".kibana/config/%s", ConfigurationSettings.DEFAULT_KIBANA_VERSION));
        assertThatResponseIsSuccessful();
    }

}
