/*
 * (C) Copyright IBM Corp. 2021, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.operation.cqf;

import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Interval;
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider;

import org.linuxforhealth.fhir.core.ResourceType;
import org.linuxforhealth.fhir.cql.helpers.DataProviderFactory;
import org.linuxforhealth.fhir.cql.helpers.FHIRBundleCursor;
import org.linuxforhealth.fhir.cql.helpers.ParameterMap;
import org.linuxforhealth.fhir.ecqm.common.MeasureReportType;
import org.linuxforhealth.fhir.exception.FHIROperationException;
import org.linuxforhealth.fhir.model.resource.Bundle;
import org.linuxforhealth.fhir.model.resource.Measure;
import org.linuxforhealth.fhir.model.resource.MeasureReport;
import org.linuxforhealth.fhir.model.resource.OperationDefinition;
import org.linuxforhealth.fhir.model.resource.Parameters;
import org.linuxforhealth.fhir.model.resource.Parameters.Parameter;
import org.linuxforhealth.fhir.model.resource.Resource;
import org.linuxforhealth.fhir.model.type.UnsignedInt;
import org.linuxforhealth.fhir.model.type.code.BundleType;
import org.linuxforhealth.fhir.registry.FHIRRegistry;
import org.linuxforhealth.fhir.search.SearchConstants;
import org.linuxforhealth.fhir.search.util.SearchHelper;
import org.linuxforhealth.fhir.server.spi.operation.FHIROperationContext;
import org.linuxforhealth.fhir.server.spi.operation.FHIROperationUtil;
import org.linuxforhealth.fhir.server.spi.operation.FHIRResourceHelpers;

public class CareGapsOperation extends AbstractMeasureOperation {

    public static final String PARAM_IN_TOPIC = "topic";
    public static final String PARAM_IN_SUBJECT = "subject";
    public static final String PARAM_OUT_RETURN = "return";

    @Override
    protected OperationDefinition buildOperationDefinition() {
        return FHIRRegistry.getInstance().getResource("http://hl7.org/fhir/OperationDefinition/Measure-care-gaps", OperationDefinition.class);
    }

    @Override
    protected Parameters doInvoke(FHIROperationContext operationContext, Class<? extends Resource> resourceType, String logicalId, String versionId,
            Parameters parameters, FHIRResourceHelpers resourceHelper, SearchHelper searchHelper) throws FHIROperationException {

        ParameterMap paramMap = new ParameterMap(parameters);

        ZoneOffset zoneOffset = getZoneOffset(paramMap);
        Interval measurementPeriod = getMeasurementPeriod(paramMap,zoneOffset);

        Parameter pTopic = paramMap.getSingletonParameter(PARAM_IN_TOPIC);
        String topic = ((org.linuxforhealth.fhir.model.type.String)pTopic.getValue()).getValue();

        Parameter pSubject = paramMap.getSingletonParameter(PARAM_IN_SUBJECT);
        String subject = ((org.linuxforhealth.fhir.model.type.String)pSubject.getValue()).getValue();

        int pageSize = 10;

        MultivaluedMap<String,String> searchParameters = new MultivaluedHashMap<>();
        searchParameters.putSingle("topic", topic);
        searchParameters.putSingle("_count", String.valueOf(pageSize));
        searchParameters.putSingle("_total", "none");

        try {
            Bundle bundle = resourceHelper.doSearch(ResourceType.MEASURE.value(), null, null, searchParameters, null);

            AtomicInteger pageNumber = new AtomicInteger(1);
            FHIRBundleCursor cursor = new FHIRBundleCursor(url -> {
                try {
                    searchParameters.putSingle(SearchConstants.PAGE, String.valueOf(pageNumber.incrementAndGet()));
                    return resourceHelper.doSearch(ResourceType.MEASURE.value(), null, null, searchParameters, null);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }, bundle);

            TerminologyProvider termProvider = getTerminologyProvider(resourceHelper);
            RetrieveProvider retrieveProvider = getRetrieveProvider(resourceHelper, termProvider, searchHelper);
            Map<String,DataProvider> dataProviders = DataProviderFactory.createDataProviders(retrieveProvider);

            Bundle result = processAllMeasures( cursor, subject, zoneOffset, measurementPeriod, resourceHelper, termProvider, dataProviders );
            return FHIROperationUtil.getOutputParameters(PARAM_OUT_RETURN, result);

        } catch( FHIROperationException fex ) {
            throw fex;
        } catch( Exception otherEx) {
            throw new FHIROperationException("Failed to load measures for topic " + topic, otherEx);
        }
    }

    /**
     * Evaluate all of the measures matching the specified care gap topic.
     * The measure reports are bundled together and returned as a result.
     *
     * @param cursor Provides an iterator over the Measure resources that match the topic
     * @param subject Subject for which the measures will be evaluated (e.g. Patient ID)
     * @param zoneOffset Timezone offset to be used by the CQL engine for date operations
     * @param measurementPeriod Measurement Period provided as input to all measures
     * @param resourceHelper Provides data access operations
     * @param termProvider Terminology Provider
     * @param dataProviders Data Providers
     * @return Bundle containing a MeasureReport resource for each evaluated measure.
     * @throws FHIROperationException
     */
    protected Bundle processAllMeasures(FHIRBundleCursor cursor, String subject, ZoneOffset zoneOffset, Interval measurementPeriod, FHIRResourceHelpers resourceHelper, TerminologyProvider termProvider, Map<String,DataProvider> dataProviders) throws FHIROperationException {
        Bundle.Builder reports = Bundle.builder().type(BundleType.COLLECTION);

        AtomicInteger count = new AtomicInteger(0);
        for (Object resource : cursor) {
            Measure measure = (Measure) resource;
            MeasureReport report = doMeasureEvaluation(resourceHelper, measure, zoneOffset, measurementPeriod, subject, MeasureReportType.INDIVIDUAL, termProvider, dataProviders).build();
            reports.entry( Bundle.Entry.builder().resource(report).build() );
            count.incrementAndGet();
        }

        reports.total(UnsignedInt.of(count.get()));
        return reports.build();
    }

}