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

package org.voltdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.voltcore.utils.Pair;

import com.google_voltpatches.common.net.HostAndPort;

public interface ProducerDRGateway {

    public interface DRProducerResponseHandler {
        public void notifyOfResponse(boolean success, boolean shouldRetry, String failureCause);
    }

    static class ClusterIdentity {
        private final byte m_clusterId;
        private final long m_clusterCreationId;

        public ClusterIdentity(byte clusterId, long clusterCreationId) {
            m_clusterId = clusterId;
            m_clusterCreationId = clusterCreationId;
        }

        public byte getClusterId() {
            return m_clusterId;
        }

        public long getClusterCreationId() {
            return m_clusterCreationId;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + Byte.hashCode(m_clusterId);
            hash = 31 * hash + Long.hashCode(m_clusterCreationId);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClusterIdentity)) {
                return false;
            }
            ClusterIdentity that = (ClusterIdentity) obj;
            return that.m_clusterId == m_clusterId &&
                   that.m_clusterCreationId == m_clusterCreationId;
        }

        @Override
        public String toString() {
            return m_clusterId + "/" + m_clusterCreationId;
        }
    }

    static class MeshMemberInfo {
        public MeshMemberInfo(ClusterIdentity clusterIdentity, int partitionCount,
                int protocolVersion, List<HostAndPort> nodes) {
            m_clusterIdentity = clusterIdentity;
            m_partitionCount = partitionCount;
            m_protocolVersion = protocolVersion;
            m_nodes = nodes;
        }

        public MeshMemberInfo(ClusterIdentity clusterIdentity, List<HostAndPort> nodes) {
            this(clusterIdentity, 0, 0, nodes);
        }

        public MeshMemberInfo(MeshMemberInfo staleNodeInfo, List<HostAndPort> nodes) {
            m_clusterIdentity = staleNodeInfo.m_clusterIdentity;
            m_protocolVersion = staleNodeInfo.m_protocolVersion;
            m_partitionCount = staleNodeInfo.m_partitionCount;
            m_nodes = nodes;
        }

        public static MeshMemberInfo createFromHostStrings(ClusterIdentity clusterIdentity, int partitionCount,
                int protocolVersion, List<String> nodes) {
            List<HostAndPort> hostAndPorts = new ArrayList<>(nodes.size());
            for (String hostPortString : nodes) {
                hostAndPorts.add(HostAndPort.fromString(hostPortString));
            }
            return new MeshMemberInfo(clusterIdentity, partitionCount, protocolVersion, hostAndPorts);
        }

        public ClusterIdentity getClusterIdentity() { return m_clusterIdentity; }
        public final ClusterIdentity m_clusterIdentity;
        /**
         * ProtocolVersion may or may not be valid depending on who generates this object
         */
        public final int m_protocolVersion;
        /**
         * Number of partitions in this cluster (excluding MP Site)
         */
        public final int m_partitionCount;
        /**
         * This is either the configured (by conversation file) HostAndPort pairs or the
         * HostAndPort pairs found in the MeshQuery response
         */
        public final List<HostAndPort> m_nodes;
    }

    /**
     * Start the main thread and the state machine, wait until all nodes converge on the initial state.
     * @throws IOException
     */
    public void startAndWaitForGlobalAgreement() throws IOException;

    /**
     * Truncate the DR log using the snapshot restore truncation point cached
     * earlier. This is called on recover before the command log replay starts
     * to drop all binary logs generated after the snapshot. Command log replay
     * will recreate those binary logs.
     */
    public void truncateDRLog();

    /**
     * Getter for collecting the set of conversations in the producer conversation file at
     * initialization time. If we have been initialized with clusters 5 and 8, and we connect
     * to cluster 5 but id does not know about cluster 8 we need to set a StartCursor immediately
     * before we even subscribe, if any conversation was the dataSource for this cluster, it
     * will be assigned in Pair.first. If Pair.first is -1, it means that this cluster was the
     * original leader and the data on this cluster was not derived from a sync snapshot
     */
    public Pair<ClusterIdentity, List<MeshMemberInfo>> getInitialConversations();

    /**
     * Start listening on the ports
     */
    public abstract void startListening(boolean drProducerEnabled, int listenPort, String portInterface) throws IOException;

    /**
     * @return true if bindPorts has been called.
     */
    public abstract boolean isStarted();

    /**
     * Queues up a task to move all the InvocationBuffers to the PersistentBinaryDeque
     * @param nofsync do not force the sync to disk (when True)
     * @return the FutureTask indicating completion
     */
    public abstract void forceAllBuffersToDisk(boolean nofsync);

    public abstract boolean isActive();
    public abstract void setActive(boolean active);

    public abstract void start();
    public abstract void shutdown() throws InterruptedException;

    public abstract void updateCatalog(final CatalogContext catalog, final int listenPort);

    public abstract ClusterIdentity getDRClusterIdentity();

    public void cacheSnapshotRestoreTruncationPoint(Map<Integer, Long> sequenceNumbers);

    public void cacheRejoinStartDRSNs(Map<Integer, Long> sequenceNumbers);

    /**
     * Clear all queued DR buffers for a master, useful when the replica goes away
     */
    public void deactivateDR();

    public void deactivateDR(byte clusterId);

    public void activateDRProducer();

    /**
     * Blocks until snaphot is generated for the specified cluster id.
     * If the snapshot has already been generated, this will return immediately
     *
     * @param forClusterId the cluster for which producer should generate snapshot.
     *        This is used and has a meaningful value only in MULTICLUSTER_PROTOCOL_VERSION
     *        or higher.
     */
    public void blockOnSyncSnapshotGeneration(byte forClusterId);

    /**
     * Sets the DR protocol version with EE. This will also generate a <code>DR_STREAM_START</code>
     * event for all partitions, if <code>genStreamStart</code> flag is true.
     *
     * @param drVersion the DR protocol version that must be set with EE.
     * @param genStreamStart <code>DR_STREAM_START</code> event will be generated for all partitions
     * if this is true.
     *
     * @return Returns true if the operation was successful. False otherwise.
     */
    public boolean setDRProtocolVersion(int drVersion, boolean genStreamStart);

    /**
     * Use this to set up cursors in DR binary logs for clusters. This will initiate the process.
     * When the process is complete, the passed in handler will be notified of the status.
     *
     * @param requestedCursors the clusters for which cursors must be started
     * @param leaderClusterId ID of the cluster that needs to be marked as the snapshot source
     * @param handler callback to notify the status of the operation
     */
    public void startCursor(final List<MeshMemberInfo> requestedCursors,
            final byte leaderClusterId, final DRProducerResponseHandler handler);

    /**
     * Get the DR producer node stats. This method may block because the task
     * runs on the producer thread and it waits for the asynchronous task to
     * finish.
     * @return The producer node stats keyed by cluster IDs or null if on error
     */
    public Map<Byte, DRProducerNodeStats> getNodeDRStats();

    public void resumeAllReadersAsync();

    public void pauseAllReadersAsync();

    public void dropLocal();
}
