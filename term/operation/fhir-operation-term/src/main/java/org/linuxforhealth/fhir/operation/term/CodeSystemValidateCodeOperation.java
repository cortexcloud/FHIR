/*
 * (C) Copyright IBM Corp. 2020, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.operation.term;

import org.linuxforhealth.fhir.exception.FHIROperationException;
import org.linuxforhealth.fhir.model.resource.CodeSystem;
import org.linuxforhealth.fhir.model.resource.OperationDefinition;
import org.linuxforhealth.fhir.model.resource.Parameters;
import org.linuxforhealth.fhir.model.resource.Resource;
import org.linuxforhealth.fhir.model.type.CodeableConcept;
import org.linuxforhealth.fhir.model.type.Coding;
import org.linuxforhealth.fhir.model.type.Element;
import org.linuxforhealth.fhir.model.type.code.IssueType;
import org.linuxforhealth.fhir.registry.FHIRRegistry;
import org.linuxforhealth.fhir.search.util.SearchHelper;
import org.linuxforhealth.fhir.server.spi.operation.FHIROperationContext;
import org.linuxforhealth.fhir.server.spi.operation.FHIRResourceHelpers;
import org.linuxforhealth.fhir.term.service.ValidationOutcome;
import org.linuxforhealth.fhir.term.service.ValidationParameters;
import org.linuxforhealth.fhir.term.service.exception.FHIRTermServiceException;

public class CodeSystemValidateCodeOperation extends AbstractTermOperation {
    @Override
    protected OperationDefinition buildOperationDefinition() {
        return FHIRRegistry.getInstance().getResource("http://hl7.org/fhir/OperationDefinition/CodeSystem-validate-code", OperationDefinition.class);
    }

    @Override
    protected Parameters doInvoke(
            FHIROperationContext operationContext,
            Class<? extends Resource> resourceType,
            String logicalId,
            String versionId,
            Parameters parameters,
            FHIRResourceHelpers resourceHelper,
            SearchHelper searchHelper) throws FHIROperationException {
        try {
            CodeSystem codeSystem = getResource(operationContext, logicalId, parameters, resourceHelper, CodeSystem.class);
            Element codedElement = getCodedElement(parameters, "codeableConcept", "coding", "code", false);
            if (codedElement.is(Coding.class) && codedElement.as(Coding.class).getSystem() == null) {
                codedElement = codedElement.as(Coding.class).toBuilder()
                        .system(codeSystem.getUrl())
                        .build();
            }
            validate(codeSystem, codedElement);
            ValidationOutcome outcome = codedElement.is(CodeableConcept.class) ?
                    service.validateCode(codeSystem, codedElement.as(CodeableConcept.class), ValidationParameters.from(parameters)) :
                    service.validateCode(codeSystem, codedElement.as(Coding.class), ValidationParameters.from(parameters));
            return outcome.toParameters();
        } catch (FHIROperationException e) {
            throw e;
        } catch (FHIRTermServiceException e) {
            throw new FHIROperationException(e.getMessage(), e.getCause()).withIssue(e.getIssues());
        } catch (UnsupportedOperationException e) {
            throw buildExceptionWithIssue(e.getMessage(), IssueType.NOT_SUPPORTED, e);
        } catch (Exception e) {
            throw new FHIROperationException("An error occurred during the CodeSystem validate code operation", e);
        }
    }

    private void validate(CodeSystem codeSystem, Element codedElement) throws FHIROperationException {
        if (codedElement.is(CodeableConcept.class)) {
            CodeableConcept codeableConcept = codedElement.as(CodeableConcept.class);
            for (Coding coding : codeableConcept.getCoding()) {
                if (coding.getSystem() != null && codeSystem.getUrl() != null && coding.getSystem().equals(codeSystem.getUrl()) && (coding.getVersion() == null ||
                        codeSystem.getVersion() == null ||
                        coding.getVersion().equals(codeSystem.getVersion()))) {
                    return;
                }
            }
            throw buildExceptionWithIssue("CodeableConcept does not contain a coding element that matches the specified CodeSystem url and/or version", IssueType.INVALID);
        }
        // codedElement.is(Coding.class)
        Coding coding = codedElement.as(Coding.class);
        if (coding.getSystem() != null && codeSystem.getUrl() != null && !coding.getSystem().equals(codeSystem.getUrl())) {
            throw buildExceptionWithIssue("Coding system does not match the specified CodeSystem url", IssueType.INVALID);
        }
        if (coding.getVersion() != null && codeSystem.getVersion() != null && !coding.getVersion().equals(codeSystem.getVersion())) {
            throw buildExceptionWithIssue("Coding version does not match the specified CodeSystem version", IssueType.INVALID);
        }
    }
}