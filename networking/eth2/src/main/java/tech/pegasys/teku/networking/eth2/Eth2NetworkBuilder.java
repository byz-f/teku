/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.connection.ReputationManager;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetwork;
import tech.pegasys.teku.networking.p2p.network.NetworkConfig;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.time.TimeProvider;

public class Eth2NetworkBuilder {

  private NetworkConfig config;
  private Eth2Config eth2Config;
  private EventBus eventBus;
  private RecentChainData recentChainData;
  private StorageQueryChannel historicalChainData;
  private MetricsSystem metricsSystem;
  private List<RpcMethod> rpcMethods = new ArrayList<>();
  private List<PeerHandler> peerHandlers = new ArrayList<>();
  private TimeProvider timeProvider;

  private Eth2NetworkBuilder() {}

  public static Eth2NetworkBuilder create() {
    return new Eth2NetworkBuilder();
  }

  public Eth2Network build() {
    validate();

    // Setup eth2 handlers
    final AttestationSubnetService attestationSubnetService = new AttestationSubnetService();
    final RpcEncoding rpcEncoding =
        eth2Config.isSnappyCompressionEnabled() ? RpcEncoding.SSZ_SNAPPY : RpcEncoding.SSZ;
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(
            recentChainData,
            historicalChainData,
            metricsSystem,
            attestationSubnetService,
            rpcEncoding);
    final Collection<RpcMethod> eth2RpcMethods = eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    // Build core network and inject eth2 handlers
    final DiscoveryNetwork<?> network = buildNetwork();

    final GossipEncoding gossipEncoding =
        eth2Config.isSnappyCompressionEnabled() ? GossipEncoding.SSZ_SNAPPY : GossipEncoding.SSZ;
    return new ActiveEth2Network(
        network,
        eth2PeerManager,
        eventBus,
        recentChainData,
        gossipEncoding,
        attestationSubnetService);
  }

  protected DiscoveryNetwork<?> buildNetwork() {
    final ReputationManager reputationManager =
        new ReputationManager(timeProvider, Constants.REPUTATION_MANAGER_CAPACITY);
    return DiscoveryNetwork.create(
        new LibP2PNetwork(config, reputationManager, metricsSystem, rpcMethods, peerHandlers),
        reputationManager,
        config);
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eth2Config", eth2Config);
    assertNotNull("eventBus", eventBus);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("chainStorageClient", recentChainData);
    assertNotNull("timeProvider", timeProvider);
  }

  private void assertNotNull(String fieldName, Object fieldValue) {
    checkState(fieldValue != null, "Field " + fieldName + " must be set.");
  }

  public Eth2NetworkBuilder config(final NetworkConfig config) {
    checkNotNull(config);
    this.config = config;
    return this;
  }

  public Eth2NetworkBuilder eth2Config(final Eth2Config eth2Config) {
    checkNotNull(eth2Config);
    this.eth2Config = eth2Config;
    return this;
  }

  public Eth2NetworkBuilder eventBus(final EventBus eventBus) {
    checkNotNull(eventBus);
    this.eventBus = eventBus;
    return this;
  }

  public Eth2NetworkBuilder historicalChainData(final StorageQueryChannel historicalChainData) {
    checkNotNull(historicalChainData);
    this.historicalChainData = historicalChainData;
    return this;
  }

  public Eth2NetworkBuilder recentChainData(final RecentChainData recentChainData) {
    checkNotNull(recentChainData);
    this.recentChainData = recentChainData;
    return this;
  }

  public Eth2NetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2NetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public Eth2NetworkBuilder rpcMethod(final RpcMethod rpcMethod) {
    checkNotNull(rpcMethod);
    rpcMethods.add(rpcMethod);
    return this;
  }

  public Eth2NetworkBuilder peerHandler(final PeerHandler peerHandler) {
    checkNotNull(peerHandler);
    peerHandlers.add(peerHandler);
    return this;
  }
}
