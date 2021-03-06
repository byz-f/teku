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

package tech.pegasys.teku.validator.client;

import java.util.Map;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.validator.anticorruption.ValidatorAnticorruptionLayer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;

public class ValidatorClientService extends Service {
  private final EventChannels eventChannels;
  private final ValidatorTimingChannel validatorTimingChannel;

  private ValidatorClientService(
      final EventChannels eventChannels, final ValidatorTimingChannel validatorTimingChannel) {
    this.eventChannels = eventChannels;
    this.validatorTimingChannel = validatorTimingChannel;
  }

  public static ValidatorClientService create(final ServiceConfig config) {
    final Map<BLSPublicKey, Validator> validators =
        ValidatorLoader.initializeValidators(config.getConfig());
    final EventChannels eventChannels = config.getEventChannels();
    final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
    final RetryingDutyLoader dutyLoader = createDutyLoader(config, asyncRunner, validators);
    final DutyScheduler dutyScheduler = new DutyScheduler(dutyLoader);

    ValidatorAnticorruptionLayer.initAnticorruptionLayer(config);

    return new ValidatorClientService(eventChannels, dutyScheduler);
  }

  private static RetryingDutyLoader createDutyLoader(
      final ServiceConfig config,
      final AsyncRunner asyncRunner,
      final Map<BLSPublicKey, Validator> validators) {
    final ValidatorApiChannel validatorApiChannel =
        config.getEventChannels().getPublisher(ValidatorApiChannel.class);
    final ForkProvider forkProvider = new ForkProvider(asyncRunner, validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel);
    return new RetryingDutyLoader(
        asyncRunner,
        new ValidatorApiDutyLoader(
            validatorApiChannel,
            forkProvider,
            () -> new ScheduledDuties(validatorDutyFactory),
            validators));
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels.subscribe(ValidatorTimingChannel.class, validatorTimingChannel);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
