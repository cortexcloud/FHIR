/*
 * (C) Copyright IBM Corp. 2016,2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence.test.common;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ibm.fhir.config.FHIRRequestContext;
import com.ibm.fhir.model.resource.Basic;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.test.TestUtil;
import com.ibm.fhir.model.type.Code;
import com.ibm.fhir.model.type.Coding;
import com.ibm.fhir.model.type.Element;
import com.ibm.fhir.model.type.Extension;
import com.ibm.fhir.model.type.Integer;
import com.ibm.fhir.model.type.Meta;
import com.ibm.fhir.persistence.MultiResourceResult;
import com.ibm.fhir.persistence.context.FHIRHistoryContext;
import com.ibm.fhir.persistence.context.FHIRPersistenceContext;
import com.ibm.fhir.persistence.context.FHIRPersistenceContextFactory;
import com.ibm.fhir.persistence.exception.FHIRPersistenceException;

/**
 * This class contains a collection of search result sorting related tests that will be run against
 * each of the various persistence layer implementations that implement a subclass of this class.
 */
public abstract class AbstractPagingTest extends AbstractPersistenceTest {
    Basic resource1;
    Basic resource2;
    Basic resource3;
    
    @BeforeClass
    public void createResources() throws Exception {
        FHIRRequestContext.get().setTenantId("all");
        
        Basic resource = TestUtil.readExampleResource("json/ibm/minimal/Basic-1.json");
        
        Basic.Builder resource1Builder = resource.toBuilder();
        Basic.Builder resource2Builder = resource.toBuilder();
        Basic.Builder resource3Builder = resource.toBuilder();
        
        // number
        resource1Builder.extension(extension("http://example.org/integer", Integer.of(1)));
        resource2Builder.extension(extension("http://example.org/integer", Integer.of(2)));
        resource3Builder.extension(extension("http://example.org/integer", Integer.of(3)));
        
        // save them in-order so that lastUpdated goes from 1 -> 3 as well
        resource1 = persistence.create(getDefaultPersistenceContext(), resource1Builder.meta(tag("pagingTest")).build()).getResource();
        resource2 = persistence.create(getDefaultPersistenceContext(), resource2Builder.meta(tag("pagingTest")).build()).getResource();
        resource3 = persistence.create(getDefaultPersistenceContext(), resource3Builder.meta(tag("pagingTest")).build()).getResource();
        
        // update resource3 two times so we have 3 different versions
        resource3 = persistence.update(getDefaultPersistenceContext(), resource3.getId().getValue(), resource3).getResource();
        resource3 = persistence.update(getDefaultPersistenceContext(), resource3.getId().getValue(), resource3).getResource();
    }
    
    @AfterClass
    public void removeSavedResourcesAndResetTenant() throws Exception {
        Resource[] resources = {resource1, resource2, resource3};
        if (persistence.isDeleteSupported()) {
            for (Resource resource : resources) {
                persistence.delete(getDefaultPersistenceContext(), Basic.class, resource.getId().getValue());
            }
            if (persistence.isTransactional()) {
                persistence.getTransaction().commit();
            }
        }
        FHIRRequestContext.get().setTenantId("default");
    }
    
    // This test assumes sorting is working, as tested in the AbstractSortTest
    @Test
    public void testSearchPaging() throws Exception {
        Map<String, List<String>> queryParameters;
        List<Resource> results;
        
        queryParameters = new HashMap<>();
        queryParameters.put("_sort", Collections.singletonList("integer"));
        queryParameters.put("_tag", Collections.singletonList("pagingTest"));
        queryParameters.put("_page", Collections.singletonList("1"));
        results = runQueryTest(Basic.class, queryParameters, 1);
        assertEquals(results.size(), 1, "expected number of results");
        assertNotNull(findResourceInResponse(resource1, results));
        
        queryParameters = new HashMap<>();
        queryParameters.put("_sort", Collections.singletonList("integer"));
        queryParameters.put("_tag", Collections.singletonList("pagingTest"));
        queryParameters.put("_page", Collections.singletonList("2"));
        results = runQueryTest(Basic.class, queryParameters, 1);
        assertEquals(results.size(), 1, "expected number of results");
        assertNotNull(findResourceInResponse(resource2, results));
        
        queryParameters = new HashMap<>();
        queryParameters.put("_sort", Collections.singletonList("integer"));
        queryParameters.put("_tag", Collections.singletonList("pagingTest"));
        queryParameters.put("_page", Collections.singletonList("3"));
        results = runQueryTest(Basic.class, queryParameters, 1);
        assertEquals(results.size(), 1, "expected number of results");
        assertNotNull(findResourceInResponse(resource3, results));
    }
    
    // history results should be sorted with oldest versions last
    @Test
    public void testHistoryPaging() throws Exception {
        FHIRHistoryContext historyContext;
        FHIRPersistenceContext context;
        MultiResourceResult<? extends Basic> result;
        List<? extends Basic> results;
        
        historyContext = FHIRPersistenceContextFactory.createHistoryContext();
        historyContext.setPageSize(1);
        historyContext.setPageNumber(1);
        context = this.getPersistenceContextForHistory(historyContext);
        
        result = persistence.history(context, resource3.getClass(), resource3.getId().getValue());
        assertTrue(result.isSuccess());
        results = result.getResource();
        assertEquals(results.size(), 1, "expected number of results");
        assertEquals(results.get(0).getMeta().getVersionId().getValue(), "3", "expected version");
        
        
        historyContext = FHIRPersistenceContextFactory.createHistoryContext();
        historyContext.setPageSize(1);
        historyContext.setPageNumber(2);
        context = this.getPersistenceContextForHistory(historyContext);
        
        result = persistence.history(context, resource3.getClass(), resource3.getId().getValue());
        assertTrue(result.isSuccess());
        results = result.getResource();
        assertEquals(results.size(), 1, "expected number of results");
        assertEquals(results.get(0).getMeta().getVersionId().getValue(), "2", "expected version");
        
        
        historyContext = FHIRPersistenceContextFactory.createHistoryContext();
        historyContext.setPageSize(1);
        historyContext.setPageNumber(3);
        context = this.getPersistenceContextForHistory(historyContext);
        
        result = persistence.history(context, resource3.getClass(), resource3.getId().getValue());
        assertTrue(result.isSuccess());
        results = result.getResource();
        assertEquals(results.size(), 1, "expected number of results");
        assertEquals(results.get(0).getMeta().getVersionId().getValue(), "1", "expected version");
    }
    
    @Test(expectedExceptions = FHIRPersistenceException.class)
    public void testInvalidPage0() throws Exception {
        Map<String, List<String>> queryParameters;
        queryParameters = new HashMap<>();
        queryParameters.put("_sort", Collections.singletonList("integer"));
        queryParameters.put("_tag", Collections.singletonList("pagingTest"));
        queryParameters.put("_page", Collections.singletonList("0"));
        runQueryTest(Basic.class, queryParameters, 1);
    }
    
    @Test(expectedExceptions = FHIRPersistenceException.class)
    public void testInvalidPage4() throws Exception {
        Map<String, List<String>> queryParameters;
        queryParameters = new HashMap<>();
        queryParameters.put("_sort", Collections.singletonList("integer"));
        queryParameters.put("_tag", Collections.singletonList("pagingTest"));
        queryParameters.put("_page", Collections.singletonList("4"));
        runQueryTest(Basic.class, queryParameters, 1);
    }
    
    private Meta tag(String tag) {
        return Meta.builder()
                   .tag(Coding.builder()
                              .code(Code.of(tag))
                              .build())
                   .build();
    }
    
    private Extension extension(String url, Element value) {
        return Extension.builder()
                        .url(url)
                        .value(value)
                        .build();
    }
}
