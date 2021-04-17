/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence;

/**
 * Wraps the output from the Erase DAO
 */
public class ResourceEraseRecord {

    private boolean partial = false;
    private Integer total = -1;

    private Status status = Status.PARTIAL;

    public ResourceEraseRecord() {
        // NOP
    }

    public ResourceEraseRecord(boolean partial) {
        this.partial = partial;
    }

    /**
     * @return the partial
     */
    public boolean isPartial() {
        return partial;
    }

    /**
     * @param partial
     *            the partial to set
     */
    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    /**
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @param status
     *            the status to set
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * @return the total
     */
    public Integer getTotal() {
        return total;
    }

    /**
     * @param total
     *            the total to set
     */
    public void setTotal(Integer total) {
        this.total = total;
    }

    /**
     * The outcome status for the Resource Erase operation
     */
    public enum Status {
        NOT_FOUND,
        PARTIAL,
        DONE,
        VERSION,
        NOT_SUPPORTED_LATEST,
        NOT_SUPPORTED_GREATER
    }

    @Override
    public String toString() {
        return "ResourceEraseRecord [partial=" + partial + ", total=" + total + ", status=" + status + "]";
    }

}