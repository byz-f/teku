/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.pow;

import com.google.common.primitives.UnsignedLong;
import io.reactivex.Flowable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import tech.pegasys.teku.util.async.SafeFuture;

public class ThrottlingEth1Provider implements Eth1Provider {
  private final Eth1Provider delegate;
  private final int maximumConcurrentRequests;
  private final Queue<Runnable> queuedRequests = new ConcurrentLinkedQueue<>();
  private int inflightRequestCount = 0;

  public ThrottlingEth1Provider(final Eth1Provider delegate, final int maximumConcurrentRequests) {
    this.delegate = delegate;
    this.maximumConcurrentRequests = maximumConcurrentRequests;
  }

  @Override
  public Flowable<Block> getLatestBlockFlowable() {
    return delegate.getLatestBlockFlowable();
  }

  @Override
  public SafeFuture<Block> getEth1BlockFuture(final UnsignedLong blockNumber) {
    return queueRequest(() -> delegate.getEth1BlockFuture(blockNumber));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1BlockFuture(final String blockHash) {
    return queueRequest(() -> delegate.getGuaranteedEth1BlockFuture(blockHash));
  }

  @Override
  public SafeFuture<Block> getGuaranteedEth1BlockFuture(final UnsignedLong blockNumber) {
    return queueRequest(() -> delegate.getGuaranteedEth1BlockFuture(blockNumber));
  }

  @Override
  public SafeFuture<Block> getEth1BlockFuture(final String blockHash) {
    return queueRequest(() -> delegate.getEth1BlockFuture(blockHash));
  }

  @Override
  public SafeFuture<Block> getLatestEth1BlockFuture() {
    return queueRequest(delegate::getLatestEth1BlockFuture);
  }

  @Override
  public SafeFuture<EthCall> ethCall(
      final String from, final String to, final String data, final UnsignedLong blockNumber) {
    return queueRequest(() -> delegate.ethCall(from, to, data, blockNumber));
  }

  private <T> SafeFuture<T> queueRequest(final Supplier<SafeFuture<T>> request) {
    final SafeFuture<T> future = new SafeFuture<>();
    queuedRequests.add(
        () -> {
          final SafeFuture<T> requestFuture = request.get();
          requestFuture.propagateTo(future);
          requestFuture.always(this::requestComplete);
        });
    processQueuedRequests();
    return future;
  }

  private synchronized void requestComplete() {
    inflightRequestCount--;
    processQueuedRequests();
  }

  private synchronized void processQueuedRequests() {
    while (inflightRequestCount < maximumConcurrentRequests && !queuedRequests.isEmpty()) {
      inflightRequestCount++;
      queuedRequests.remove().run();
    }
  }
}
