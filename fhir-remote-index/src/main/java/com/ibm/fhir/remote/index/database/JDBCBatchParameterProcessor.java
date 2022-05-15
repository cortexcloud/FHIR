/*
 * (C) Copyright IBM Corp. 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.ibm.fhir.remote.index.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.fhir.persistence.exception.FHIRPersistenceException;
import com.ibm.fhir.persistence.index.DateParameter;
import com.ibm.fhir.persistence.index.LocationParameter;
import com.ibm.fhir.persistence.index.NumberParameter;
import com.ibm.fhir.persistence.index.ProfileParameter;
import com.ibm.fhir.persistence.index.QuantityParameter;
import com.ibm.fhir.persistence.index.SecurityParameter;
import com.ibm.fhir.persistence.index.StringParameter;
import com.ibm.fhir.persistence.index.TagParameter;
import com.ibm.fhir.persistence.index.TokenParameter;
import com.ibm.fhir.remote.index.api.BatchParameterProcessor;


/**
 * Processes batched parameters by pushing the values to various
 * JDBC statements
 */
public class JDBCBatchParameterProcessor implements BatchParameterProcessor {
    private static final Logger logger = Logger.getLogger(JDBCBatchParameterProcessor.class.getName());

    // A cache of the resource-type specific DAOs we've created
    private final Map<String, DistributedPostgresParameterBatch> daoMap = new HashMap<>();

    // Encapculates the statements for inserting whole-system level search params
    private final DistributedPostgresSystemParameterBatch systemDao;

    // Resource types we've touched in the current batch
    private final Set<String> resourceTypesInBatch = new HashSet<>();

    // The database connection this consumer thread is using
    private final Connection connection;

    /**
     * Public constructor
     * @param connection
     */
    public JDBCBatchParameterProcessor(Connection connection) {
        this.connection = connection;
        this.systemDao = new DistributedPostgresSystemParameterBatch(connection);        
    }

    /**
     * Close any resources we're holding to support a cleaner exit
     */
    public void close() {
        for (Map.Entry<String, DistributedPostgresParameterBatch> entry: daoMap.entrySet()) {
            entry.getValue().close();
        }
        systemDao.close();
    }

    /**
     * Start processing a new batch
     */
    public void startBatch() {
        resourceTypesInBatch.clear();
    }

    /**
     * Make sure that each statement that may contain data is cleared before we
     * retry a batch
     */
    public void reset() {
        for (String resourceType: resourceTypesInBatch) {
            DistributedPostgresParameterBatch dao = daoMap.get(resourceType);
            dao.close();
        }
        systemDao.close();
    }
    /**
     * Push any statements that have been batched but not yet executed
     * @throws FHIRPersistenceException
     */
    public void pushBatch() throws FHIRPersistenceException {
        try {
            for (String resourceType: resourceTypesInBatch) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Pushing batch for [" + resourceType + "]");
                }
                DistributedPostgresParameterBatch dao = daoMap.get(resourceType);
                try {
                    dao.pushBatch();
                } catch (SQLException x) {
                    throw new FHIRPersistenceException("pushBatch failed for '" + resourceType + "'");
                }
            }

            try {
                logger.fine("Pushing batch for whole-system parameters");
                systemDao.pushBatch();
            } catch (SQLException x) {
                throw new FHIRPersistenceException("batch insert for whole-system parameters", x);
            }
        } finally {
            // Reset the set of active resource-types ready for the next batch
            resourceTypesInBatch.clear();
        }
    }

    private DistributedPostgresParameterBatch getParameterBatchDao(String resourceType) {
        resourceTypesInBatch.add(resourceType);
        DistributedPostgresParameterBatch dao = daoMap.get(resourceType);
        if (dao == null) {
            dao = new DistributedPostgresParameterBatch(connection, resourceType);
            daoMap.put(resourceType, dao);
        }
        return dao;
    }

    @Override
    public Short encodeShardKey(String requestShard) {
        if (requestShard != null) {
            return Short.valueOf((short)requestShard.hashCode());
        } else {
            return null;
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, StringParameter parameter) throws FHIRPersistenceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process string parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                + parameter.toString() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addString(logicalResourceId, parameterNameValue.getParameterNameId(), parameter.getValue(), parameter.getValue().toLowerCase(), parameter.getCompositeId(), shardKey);

            if (parameter.isSystemParam()) {
                systemDao.addString(logicalResourceId, parameterNameValue.getParameterNameId(), parameter.getValue(), parameter.getValue().toLowerCase(), parameter.getCompositeId(), shardKey);
            }
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting string params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, NumberParameter p) throws FHIRPersistenceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process number parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addNumber(logicalResourceId, parameterNameValue.getParameterNameId(), p.getValue(), p.getLowValue(), p.getHighValue(), p.getCompositeId(), shardKey);
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting string params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, QuantityParameter p, CodeSystemValue codeSystemValue) throws FHIRPersistenceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process quantity parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addQuantity(logicalResourceId, parameterNameValue.getParameterNameId(), codeSystemValue.getCodeSystemId(), p.getValueCode(), p.getValueNumber(), p.getValueNumberLow(), p.getValueNumberHigh(), p.getCompositeId(), shardKey);
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting quantity params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, LocationParameter p) throws FHIRPersistenceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process location parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addLocation(logicalResourceId, parameterNameValue.getParameterNameId(), p.getValueLatitude(), p.getValueLongitude(), p.getCompositeId(), shardKey);
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting location params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, DateParameter p) throws FHIRPersistenceException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process date parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addDate(logicalResourceId, parameterNameValue.getParameterNameId(), p.getValueDateStart(), p.getValueDateEnd(), p.getCompositeId(), shardKey);
            if (p.isSystemParam()) {
                systemDao.addDate(logicalResourceId, parameterNameValue.getParameterNameId(), p.getValueDateStart(), p.getValueDateEnd(), p.getCompositeId(), shardKey);
            }
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting date params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, TokenParameter p,
        CommonTokenValue commonTokenValue) throws FHIRPersistenceException {

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process token parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "] [" + commonTokenValue.getCommonTokenValueId() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addResourceTokenRef(logicalResourceId, parameterNameValue.getParameterNameId(), commonTokenValue.getCommonTokenValueId(), p.getRefVersionId(), p.getCompositeId(), shardKey);
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting token params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, TagParameter p,
        CommonTokenValue commonTokenValue) throws FHIRPersistenceException {

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process tag parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "] [" + commonTokenValue.getCommonTokenValueId() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addTag(logicalResourceId, commonTokenValue.getCommonTokenValueId(), shardKey);
            
            if (p.isSystemParam()) {
                systemDao.addTag(logicalResourceId, commonTokenValue.getCommonTokenValueId(), shardKey);
            }
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting tag params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, ProfileParameter p,
        CommonCanonicalValue commonCanonicalValue) throws FHIRPersistenceException {

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process profile parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "] [" + commonCanonicalValue.getCanonicalId() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addProfile(logicalResourceId, commonCanonicalValue.getCanonicalId(), p.getVersion(), p.getFragment(), shardKey);
            if (p.isSystemParam()) {
                systemDao.addProfile(logicalResourceId, commonCanonicalValue.getCanonicalId(), p.getVersion(), p.getFragment(), shardKey);
            }
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting profile params for '" + resourceType + "'");
        }
    }

    @Override
    public void process(String requestShard, String resourceType, String logicalId, long logicalResourceId, ParameterNameValue parameterNameValue, SecurityParameter p,
        CommonTokenValue commonTokenValue) throws FHIRPersistenceException {

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("process security parameter [" + requestShard + "] [" + resourceType + "] [" + logicalId + "] [" + logicalResourceId + "] [" + parameterNameValue.getParameterName() + "] ["
                    + p.toString() + "] [" + commonTokenValue.getCommonTokenValueId() + "]");
        }

        try {
            DistributedPostgresParameterBatch dao = getParameterBatchDao(resourceType);
            final Short shardKey = encodeShardKey(requestShard);
            dao.addSecurity(logicalResourceId, commonTokenValue.getCommonTokenValueId(), shardKey);
            
            if (p.isSystemParam()) {
                systemDao.addSecurity(logicalResourceId, commonTokenValue.getCommonTokenValueId(), shardKey);
            }
        } catch (SQLException x) {
            throw new FHIRPersistenceException("Failed inserting security params for '" + resourceType + "'");
        }
    }
}
