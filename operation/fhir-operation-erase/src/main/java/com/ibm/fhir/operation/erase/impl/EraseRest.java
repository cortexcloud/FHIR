/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.operation.erase.impl;

import com.ibm.fhir.exception.FHIROperationException;
import com.ibm.fhir.persistence.erase.EraseDTO;

/**
 * Erase specifies a common set of methods to control access, and process the
 * input from the REST layer to the data access layer.
 */
public interface EraseRest {
    /**
     * verifies the authorization to the operation based on the allowedRoles.
     * checks if the user calling the erase operation is authorized.
     *
     * @throws FHIROperationException
     */
    void authorize() throws FHIROperationException;

    /**
     * checks if the tenant has enabled the Erase operation.
     * @throws FHIROperationException the Erase operation is not enabled
     */
    void enabled() throws FHIROperationException;

    /**
     * verifies the HttpMethod and incoming parameters are supported,
     * and creates an intermediate EraseBean
     *
     * @throws FHIROperationException
     */
    EraseDTO verify() throws FHIROperationException;
}