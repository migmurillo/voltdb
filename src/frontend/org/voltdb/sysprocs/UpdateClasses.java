/* This file is part of VoltDB.
 * Copyright (C) 2008-2017 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.sysprocs;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.voltcore.logging.VoltLogger;
import org.voltdb.ClientResponseImpl;
import org.voltdb.VoltDB;
import org.voltdb.VoltProcedure.VoltAbortException;
import org.voltdb.client.ClientResponse;
import org.voltdb.compiler.CatalogChangeResult;
import org.voltdb.compiler.CatalogChangeResult.PrepareDiffFailureException;
import org.voltdb.compiler.deploymentfile.DrRoleType;

/**
 * Non-transactional procedure to implement public @UpdateClasses system procedure.
 *
 */
public class UpdateClasses extends UpdateApplicationBase {

    VoltLogger log = new VoltLogger("HOST");

    public CompletableFuture<ClientResponse> run(byte[] jarfileBytes, String classesToDeleteSelector) throws Exception {
        DrRoleType drRole = DrRoleType.fromValue(VoltDB.instance().getCatalogContext().getCluster().getDrrole());

        boolean useDDLSchema = VoltDB.instance().getCatalogContext().cluster.getUseddlschema();

        if (!allowPausedModeWork(false, isAdminConnection())) {
            return makeQuickResponse(
                    ClientResponse.SERVER_UNAVAILABLE,
                    "Server is paused and is available in read-only mode - please try again later.");
        }
        // We have an @UAC.  Is it okay to run it?
        // If we weren't provided operationBytes, it's a deployment-only change and okay to take
        // master and adhoc DDL method chosen
        if (!useDDLSchema) {
            return makeQuickResponse(
                    ClientResponse.GRACEFUL_FAILURE,
                    "Cluster is configured to use @UpdateApplicationCatalog " +
                    "to change application schema.  Use of @UpdateClasses is forbidden.");
        }

        CatalogChangeResult ccr = null;
        try {
            ccr = prepareApplicationCatalogDiff("@UpdateClasses",
                                                jarfileBytes,
                                                classesToDeleteSelector,
                                                new String[0],
                                                null,
                                                false, /* isPromotion */
                                                drRole,
                                                useDDLSchema,
                                                false,
                                                getHostname(),
                                                getUsername());
        }
        catch (PrepareDiffFailureException pe) {
            hostLog.info("A request to update the loaded classes has been rejected. More info returned to client.");
            return makeQuickResponse(pe.statusCode, pe.getMessage());
        }

        // Log something useful about catalog upgrades when they occur.
        if (ccr.upgradedFromVersion != null) {
            hostLog.info(String.format("In order to update the application catalog it was "
                    + "automatically upgraded from version %s.",
                    ccr.upgradedFromVersion));
        }

        // case for @CatalogChangeResult
        if (ccr.encodedDiffCommands.trim().length() == 0) {
            return makeQuickResponse(ClientResponseImpl.SUCCESS, "Catalog update with no changes was skipped.");
        }

        // Write the new catalog to a temporary jar file
        CompletableFuture<Map<Integer,ClientResponse>> cf =
                                                      callNTProcedureOnAllHosts(
                                                      "@WriteCatalog",
                                                      ccr.encodedDiffCommands,
                                                      ccr.catalogHash,
                                                      ccr.catalogBytes,
                                                      ccr.expectedCatalogVersion,
                                                      ccr.deploymentString,
                                                      ccr.tablesThatMustBeEmpty,
                                                      ccr.reasonsForEmptyTables,
                                                      ccr.requiresSnapshotIsolation ? 1 : 0,
                                                      ccr.worksWithElastic ? 1 : 0,
                                                      ccr.deploymentHash,
                                                      ccr.requireCatalogDiffCmdsApplyToEE ? 1 : 0,
                                                      ccr.hasSchemaChange ?  1 : 0,
                                                      ccr.requiresNewExportGeneration ? 1 : 0);

        Map<Integer, ClientResponse>  map = null;
        try {
            map = cf.get();
        } catch (InterruptedException | ExecutionException e) {
            hostLog.warn("A request to update the loaded classes has failed. More info returned to client.");
            throw new VoltAbortException(e);
        }

        if (map != null) {
            for (Entry<Integer, ClientResponse> entry : map.entrySet()) {
                if (entry.getValue().getStatus() != ClientResponseImpl.SUCCESS) {
                    hostLog.warn("A response from one host for writing the catalog jar has failed.");
                    hostLog.warn("Warning message: " + entry.getValue().getStatusString());
                    throw new VoltAbortException("A response from host " + entry.getKey() +
                                                 " for writing the catalog jar has failed.");
                }
            }
        }

        return callProcedure("@UpdateCore",
                              ccr.encodedDiffCommands,
                              ccr.catalogHash,
                              ccr.catalogBytes,
                              ccr.expectedCatalogVersion,
                              ccr.deploymentString,
                              ccr.tablesThatMustBeEmpty,
                              ccr.reasonsForEmptyTables,
                              ccr.requiresSnapshotIsolation ? 1 : 0,
                              ccr.worksWithElastic ? 1 : 0,
                              ccr.deploymentHash,
                              ccr.requireCatalogDiffCmdsApplyToEE ? 1 : 0,
                              ccr.hasSchemaChange ?  1 : 0,
                              ccr.requiresNewExportGeneration ? 1 : 0);
    }
}
