/*
 * (C) Copyright IBM Corp. 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.ig.davinci.pdex.test.v200;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Collection;

import org.testng.annotations.Test;

import org.linuxforhealth.fhir.ig.davinci.pdex.PDEX200ResourceProvider;
import org.linuxforhealth.fhir.registry.resource.FHIRRegistryResource;
import org.linuxforhealth.fhir.registry.spi.FHIRRegistryResourceProvider;

public class PDEXResourceProviderTest {
    @Test
    public void testPDEXResourceProvider() {
        FHIRRegistryResourceProvider provider = new PDEX200ResourceProvider();
        Collection<FHIRRegistryResource> registryResources = provider.getRegistryResources();
        assertNotNull(registryResources);
        assertTrue(!registryResources.isEmpty());
        for (FHIRRegistryResource fhirRegistryResource : registryResources) {
            assertNotNull(fhirRegistryResource.getResource());
        }
    }
}