/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.lilycms.repository.api.BlobStoreAccess;
import org.lilycms.repository.api.Repository;
import org.lilycms.repository.api.TypeManager;
import org.lilycms.repository.avro.AvroConverter;
import org.lilycms.repository.impl.DFSBlobStoreAccess;
import org.lilycms.repository.impl.HBaseBlobStoreAccess;
import org.lilycms.repository.impl.IdGeneratorImpl;
import org.lilycms.repository.impl.InlineBlobStoreAccess;
import org.lilycms.repository.impl.RemoteRepository;
import org.lilycms.repository.impl.RemoteTypeManager;
import org.lilycms.repository.impl.SizeBasedBlobStoreAccessFactory;

/**
 * Provides remote repository implementations.
 *
 * <p>Connects to zookeeper to find out available repository nodes.
 *
 * <p>Each call to {@link #getRepository()} will return a server at random. If you are in a situation where the
 * number of clients is limited and clients are long-running (e.g. some front-end servers), you should frequently
 * request an new Repository object in order to avoid talking to the same server all the time.
 */
public class LilyClient {
    private ZooKeeper zk;
    private List<ServerNode> servers = new ArrayList<ServerNode>();
    private Set<String> serverAddresses = new HashSet<String>();
    private String lilyPath = "/lily";
    private String nodesPath = lilyPath + "/repositoryNodes";
    private String dfsUriPath = lilyPath + "/dfsUri";

    private Log log = LogFactory.getLog(getClass());
    private BlobStoreAccess dfsBlobStoreAccess;
    private BlobStoreAccess hbaseBlobStoreAccess;
    private BlobStoreAccess inlineBlobStoreAccess;
    private final String zookeeperConnectString;

    public LilyClient(String zookeeperConnectString) throws IOException, InterruptedException, KeeperException {
        this.zookeeperConnectString = zookeeperConnectString;
        zk = new ZooKeeper(zookeeperConnectString, 5000, new ZkWatcher());
        refreshServers();
    }

    public synchronized Repository getRepository() throws IOException, ServerUnavailableException {
        if (servers.size() == 0) {
            throw new ServerUnavailableException("No servers available");
        }
        int pos = (int)Math.floor(Math.random() * servers.size());
        ServerNode server = servers.get(pos);
        if (server.repository == null) {
            // TODO if this particular repository server would not be reachable, we could retry a number
            // of times with other servers instead.
            constructRepository(server);
        }
        return server.repository;
    }

    private void constructRepository(ServerNode server) throws IOException {
        AvroConverter remoteConverter = new AvroConverter();
        IdGeneratorImpl idGenerator = new IdGeneratorImpl();
        TypeManager typeManager = new RemoteTypeManager(parseAddressAndPort(server.lilyAddressAndPort),
                remoteConverter, idGenerator);
        
        SizeBasedBlobStoreAccessFactory blobStoreAccessFactory = setupBlobStoreAccess();
        
        Repository repository = new RemoteRepository(parseAddressAndPort(server.lilyAddressAndPort),
                remoteConverter, (RemoteTypeManager)typeManager, idGenerator, blobStoreAccessFactory);
        
        remoteConverter.setRepository(repository);
        server.repository = repository;
    }

    private SizeBasedBlobStoreAccessFactory setupBlobStoreAccess() throws IOException {
        Configuration configuration = HBaseConfiguration.create();
        Pair<String,String> quorumsAndPorts = extractQuorumsAndPorts(zookeeperConnectString);
        configuration.set("hbase.zookeeper.quorum", quorumsAndPorts.getFirst());
        configuration.set("hbase.zookeeper.property.clientPort", quorumsAndPorts.getSecond());
        
        FileSystem.get(getDfsUri(), configuration);
        
        dfsBlobStoreAccess = new DFSBlobStoreAccess(FileSystem.get(configuration));
        hbaseBlobStoreAccess = new HBaseBlobStoreAccess(configuration);
        inlineBlobStoreAccess = new InlineBlobStoreAccess();
        BlobStoreAccess defaultBlobStoreAccess = dfsBlobStoreAccess == null ? hbaseBlobStoreAccess : dfsBlobStoreAccess;
        SizeBasedBlobStoreAccessFactory blobStoreAccessFactory = new SizeBasedBlobStoreAccessFactory(defaultBlobStoreAccess);
        blobStoreAccessFactory.addBlobStoreAccess(5000, inlineBlobStoreAccess);
        blobStoreAccessFactory.addBlobStoreAccess(200000, hbaseBlobStoreAccess);
        return blobStoreAccessFactory;
    }
    
    private Pair<String, String> extractQuorumsAndPorts(String zookeeperConnectString) {
        StringBuilder quorumBuilder = new StringBuilder();
        StringBuilder portBuilder = new StringBuilder();
        
        String[] hostsAndPorts = zookeeperConnectString.split(",");
        boolean firstTime = true;
        for (String hostAndPort : hostsAndPorts) {
            String[] splitHostAndPort = hostAndPort.split(":");
            if (!firstTime) {
                quorumBuilder.append(",");
                portBuilder.append(",");
            }
            quorumBuilder.append(splitHostAndPort[0]);
            portBuilder.append(splitHostAndPort[1]);
        }
        return new Pair<String, String>(quorumBuilder.toString(), portBuilder.toString());
    }
    
    private URI getDfsUri()  {
        try {
            return new URI(new String(zk.getData(dfsUriPath, false, new Stat())));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get DFS URI information from Zookeeper", e);
        }
    }

    private InetSocketAddress parseAddressAndPort(String addressAndPort) {
        int colonPos = addressAndPort.indexOf(":");
        if (colonPos == -1) {
            // since these are produced by the server nodes, this should never occur
            throw new RuntimeException("Unexpected situation: invalid addressAndPort: " + addressAndPort);
        }

        String address = addressAndPort.substring(0, colonPos);
        int port = Integer.parseInt(addressAndPort.substring(colonPos + 1));

        return new InetSocketAddress(address, port);
    }

    private class ServerNode {
        private String lilyAddressAndPort;
        private Repository repository;

        public ServerNode(String lilyAddressAndPort) {
            this.lilyAddressAndPort = lilyAddressAndPort;
        }
    }

    private synchronized void refreshServers() {
        Set<String> currentServers = new HashSet<String>();
        try {
            currentServers.addAll(zk.getChildren(nodesPath, true));
        } catch (Exception e) {
            log.error("Error querying list of Lily server nodes from Zookeeper.", e);
            return;
        }

        Set<String> removedServers = new HashSet<String>();
        removedServers.addAll(serverAddresses);
        removedServers.removeAll(currentServers);

        Set<String> newServers = new HashSet<String>();
        newServers.addAll(currentServers);
        newServers.removeAll(serverAddresses);

        if (log.isDebugEnabled()) {
            log.debug("ZK watcher: # current servers in ZK: " + currentServers.size() + ", # added servers: " +
                    newServers.size() + ", # removed servers: " + removedServers.size());
        }

        // Remove removed servers
        Iterator<ServerNode> serverIt = servers.iterator();
        while (serverIt.hasNext()) {
            ServerNode server = serverIt.next();
            if (removedServers.contains(server.lilyAddressAndPort)) {
                serverIt.remove();
            }
        }
        serverAddresses.removeAll(removedServers);

        // Add new servers
        for (String server : newServers) {
            servers.add(new ServerNode(server));
            serverAddresses.add(server);
        }
    }

    private class ZkWatcher implements Watcher {
        public void process(WatchedEvent watchedEvent) {
            if (watchedEvent.getPath() != null && watchedEvent.getPath().equals(nodesPath)) {
                refreshServers();
            }
        }
    }
}