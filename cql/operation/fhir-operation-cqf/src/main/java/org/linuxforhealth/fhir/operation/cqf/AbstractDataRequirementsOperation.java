/*
 * (C) Copyright IBM Corp. 2021, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.operation.cqf;

import static org.linuxforhealth.fhir.cql.helpers.ModelHelper.concept;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.linuxforhealth.fhir.cql.Constants;
import org.linuxforhealth.fhir.exception.FHIROperationException;
import org.linuxforhealth.fhir.model.resource.Library;
import org.linuxforhealth.fhir.model.resource.Parameters;
import org.linuxforhealth.fhir.model.resource.Resource;
import org.linuxforhealth.fhir.model.type.DataRequirement;
import org.linuxforhealth.fhir.model.type.ParameterDefinition;
import org.linuxforhealth.fhir.model.type.RelatedArtifact;
import org.linuxforhealth.fhir.model.type.code.PublicationStatus;
import org.linuxforhealth.fhir.model.type.code.RelatedArtifactType;
import org.linuxforhealth.fhir.search.util.SearchHelper;
import org.linuxforhealth.fhir.server.spi.operation.AbstractOperation;
import org.linuxforhealth.fhir.server.spi.operation.FHIROperationContext;
import org.linuxforhealth.fhir.server.spi.operation.FHIROperationUtil;
import org.linuxforhealth.fhir.server.spi.operation.FHIRResourceHelpers;

public abstract class AbstractDataRequirementsOperation extends AbstractOperation {
    public static final String PARAM_OUT_RETURN = "return";

    /**
     * Perform the data requirements operation for the provided list of Library
     * resources.
     *
     * @param fhirLibraries
     *            List of library resources.
     * @return Library data requirements
     */
    protected Parameters doDataRequirements(List<Library> fhirLibraries) {
        return doDataRequirements(fhirLibraries, null);
    }

    /**
     * Perform the data requirements operation for the provided list of Library
     * resources.
     *
     * @param fhirLibraries
     *            List of library resources.
     * @param additionalRelated
     *            Supplier of additional RelatedArtifacts that should be
     *            addeded to the generated data requirements.
     *
     * @return Library data requirements
     */
    protected Parameters doDataRequirements(List<Library> fhirLibraries, Supplier<List<RelatedArtifact>> additionalRelated) {
        Library.Builder result = Library.builder()
                .status(PublicationStatus.UNKNOWN)
                .type( concept(Constants.LIBRARY_TYPE_MODEL_DEFINITION) );

        Collection<RelatedArtifact> related = new ArrayList<>();
        Collection<ParameterDefinition> libParams = new ArrayList<>();
        Collection<DataRequirement> dataReqs = new ArrayList<>();

        if( additionalRelated != null ) {
            related.addAll( additionalRelated.get() );
        }

        for( Library l : fhirLibraries ) {
            related.addAll( l.getRelatedArtifact().stream().filter( r -> r.getType().equals(RelatedArtifactType.DEPENDS_ON) ).collect(Collectors.toList()) );
            libParams.addAll( l.getParameter() );
            dataReqs.addAll( l.getDataRequirement() );
        }

        if( related.size() > 0 ) {
            // deduplication by resource
            result.relatedArtifact( related.stream()
                .collect( Collectors.toMap( ra -> ra.getResource().getValue(), Function.identity(), (x,y) -> y ) )
                .values() );
        }
        if( libParams.size() > 0 ) {
            // deduplication by parameter name
            result.parameter( libParams.stream()
                .collect( Collectors.toMap( pd -> pd.getName().getValue(), Function.identity(), (x,y) -> y ) )
                .values() );
        }
        if( dataReqs.size() > 0 ) {
            // deduplication by equality
            result.dataRequirement( dataReqs.stream()
                .collect( Collectors.toSet() ) );
        }

        return FHIROperationUtil.getOutputParameters(PARAM_OUT_RETURN, result.build());
    }

    @Override
    public abstract Parameters doInvoke(
        FHIROperationContext operationContext,
        Class<? extends Resource> resourceType,
        String logicalId, String versionId,
        Parameters parameters,
        FHIRResourceHelpers resourceHelper,
        SearchHelper searchHelper) throws FHIROperationException;
}