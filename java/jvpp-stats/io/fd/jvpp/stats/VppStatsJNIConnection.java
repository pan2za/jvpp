/*
 * Copyright (c) 2019 Cisco and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fd.jvpp.stats;

import io.fd.jvpp.VppConnection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.fd.jvpp.NativeLibraryLoader.loadLibrary;
import static java.lang.String.format;

/**
 * JNI based representation of a management connection to VPP.
 */
public final class VppStatsJNIConnection implements VppConnection {
    private static final Logger LOG = Logger.getLogger(VppStatsJNIConnection.class.getName());

    private StatsConnectionInfo connectionInfo;

    private final String clientName;
    private volatile boolean disconnected = false;

    static {
        final String libName = "libjvpp_stats_registry.so";
        try {
            loadLibrary(libName, VppStatsJNIConnection.class);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, format("Can't find vpp jni library: %s", libName), e);
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Create VPPStatsJNIConnection instance for client connecting to VPP.
     *
     * @param clientName client name instance to be used for communication. Single connection per clientName is
     *                   allowed.
     */
    public VppStatsJNIConnection(final String clientName) {
        this.clientName = Objects.requireNonNull(clientName, "Null clientName");
    }

    /**
     * Guarded by VppStatsJNIConnection.class
     */
    private static final Map<String, VppStatsJNIConnection> connections = new HashMap<>();

    /**
     * Initiate VPP connection for current instance
     * <p>
     * Multiple instances are allowed since this class is not a singleton (VPP allows multiple management connections).
     * <p>
     * However only a single connection per clientName is allowed.
     *
     * @throws IOException in case the connection could not be established
     */

    @Override
    public void connect() throws IOException {
        _connect();
    }

    private void _connect() throws IOException {

        synchronized (VppStatsJNIConnection.class) {
            if (connections.containsKey(clientName)) {
                throw new IOException("Client " + clientName + " already connected");
            }

            connectionInfo = statsConnect(clientName);
            if (connectionInfo.status != 0) {
                throw new IOException("Connection returned error " + connectionInfo.status);
            }
            connections.put(clientName, this);
        }
    }

    @Override
    public final void checkActive() {
        if (disconnected) {
            throw new IllegalStateException("Disconnected client " + clientName);
        }
    }

    @Override
    public final synchronized void close() {
        if (!disconnected) {
            disconnected = true;
            try {
                statsDisconnect();
            } finally {
                synchronized (VppStatsJNIConnection.class) {
                    connections.remove(clientName);
                }
            }
        }
    }

    public StatsConnectionInfo getStatsConnectionInfo() {
        return connectionInfo;
    }

    /**
     * VPP connection information used by plugins to reuse the connection.
     */
    public static final class StatsConnectionInfo {
        public final long queueAddress;
        public final int clientIndex;
        public final int status; // FIXME throw exception instead
        public final int pid;

        public StatsConnectionInfo(long queueAddress, int clientIndex, int status, int pid) {
            this.queueAddress = queueAddress;
            this.clientIndex = clientIndex;
            this.status = status;
            this.pid = pid;
        }
    }

    private static native StatsConnectionInfo statsConnect(String clientName);

    private static native void statsDisconnect();
}
