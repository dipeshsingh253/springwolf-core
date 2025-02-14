// SPDX-License-Identifier: Apache-2.0
package io.github.stavshamir.springwolf.asyncapi;

import com.asyncapi.v2._6_0.model.channel.ChannelItem;
import io.github.stavshamir.springwolf.asyncapi.types.AsyncAPI;
import io.github.stavshamir.springwolf.asyncapi.types.Components;
import io.github.stavshamir.springwolf.configuration.AsyncApiDocket;
import io.github.stavshamir.springwolf.configuration.AsyncApiDocketService;
import io.github.stavshamir.springwolf.schemas.SchemasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAsyncApiService implements AsyncApiService {

    /**
     * Record holding the result of AsyncAPI creation.
     *
     * @param asyncAPI
     * @param exception
     */
    private record AsyncAPIResult(AsyncAPI asyncAPI, Throwable exception) {}

    private final AsyncApiDocketService asyncApiDocketService;
    private final ChannelsService channelsService;
    private final SchemasService schemasService;
    private final List<AsyncApiCustomizer> customizers;

    private volatile AsyncAPIResult asyncAPIResult = null;

    @Override
    public AsyncAPI getAsyncAPI() {
        if (isNotInitialized()) {
            initAsyncAPI();
        }

        if (asyncAPIResult.asyncAPI != null) {
            return asyncAPIResult.asyncAPI;
        } else {
            throw new RuntimeException("Error occured during creation of AsyncAPI", asyncAPIResult.exception);
        }
    }

    /**
     * Does the 'heavy work' of building the AsyncAPI documents once. Stores the resulting
     * AsyncAPI document or alternativly a catched exception/error in the instance variable asyncAPIResult.
     *
     * @return
     */
    protected synchronized void initAsyncAPI() {
        if (this.asyncAPIResult != null) {
            return; // Double Check Idiom
        }

        try {
            log.debug("Building AsyncAPI document");

            AsyncApiDocket docket = asyncApiDocketService.getAsyncApiDocket();

            // ChannelsService must be invoked before accessing SchemasService,
            // because during channel scanning, all detected schemas are registered with
            // SchemasService.
            Map<String, ChannelItem> channels = channelsService.findChannels();

            Components components = Components.builder()
                    .schemas(schemasService.getDefinitions())
                    .build();

            AsyncAPI asyncAPI = AsyncAPI.builder()
                    .info(docket.getInfo())
                    .id(docket.getId())
                    .defaultContentType(docket.getDefaultContentType())
                    .servers(docket.getServers())
                    .channels(channels)
                    .components(components)
                    .build();

            for (AsyncApiCustomizer customizer : customizers) {
                customizer.customize(asyncAPI);
            }
            this.asyncAPIResult = new AsyncAPIResult(asyncAPI, null);
        } catch (Throwable t) {
            this.asyncAPIResult = new AsyncAPIResult(null, t);
        }
    }

    /**
     * checks whether asyncApi has internally allready been initialized or not.
     *
     * @return true if asyncApi has not allready been created and initialized.
     */
    public boolean isNotInitialized() {
        return this.asyncAPIResult == null;
    }
}
