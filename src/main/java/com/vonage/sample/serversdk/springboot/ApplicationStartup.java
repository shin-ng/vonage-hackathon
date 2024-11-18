/*
 * Copyright 2024 Vonage
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.vonage.sample.serversdk.springboot;

import com.vonage.client.application.Application;
import com.vonage.client.application.ApplicationResponseException;
import com.vonage.client.application.capabilities.Messages;
import com.vonage.client.application.capabilities.NetworkApis;
import com.vonage.client.application.capabilities.Verify;
import com.vonage.client.application.capabilities.Voice;
import com.vonage.client.common.HttpMethod;
import com.vonage.client.common.Webhook;
import static com.vonage.sample.serversdk.springboot.ApplicationConfiguration.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.logging.Logger;

@Component
public class ApplicationStartup {
    private final Logger logger = Logger.getLogger("startup");

    @Autowired
    private ApplicationConfiguration configuration;

    private Webhook buildWebhook(String endpoint) {
        return Webhook.builder()
                .address(resolveEndpoint(endpoint).toString())
                .method(HttpMethod.POST).build();
    }

    private URI resolveEndpoint(String endpoint) {
        return configuration.serverUrl.resolve(endpoint);
    }

    @PostConstruct
    public void init() {
        var ac = configuration.vonageClient.getApplicationClient();
        var appIdStr = configuration.applicationId.toString();
        try {
            var existing = ac.getApplication(appIdStr);
            var networkApis = existing.getCapabilities().getNetworkApis();
            var networkApplicationId = networkApis != null ? networkApis.getNetworkApplicationId() : null;
            var builder = Application.builder(existing).improveAi(true);
            if (networkApplicationId != null) {
                builder.addCapability(NetworkApis.builder()
                        .redirectUri(resolveEndpoint(NUMBER_VERIFICATION_REDIRECT_ENDPOINT).toString())
                        .networkApplicationId(networkApplicationId)
                        .build()
                );
            }
            builder.addCapability(Verify.builder()
                        .addWebhook(Webhook.Type.STATUS, buildWebhook(VERIFY_STATUS_ENDPOINT))
                        .build()
                )
                .addCapability(Messages.builder()
                        .addWebhook(Webhook.Type.INBOUND, buildWebhook(INBOUND_MESSAGE_ENDPOINT))
                        .addWebhook(Webhook.Type.STATUS, buildWebhook(MESSAGE_STATUS_ENDPOINT))
                        .build()
                )
                .addCapability(Voice.builder()
                        .addWebhook(Webhook.Type.ANSWER, buildWebhook(VOICE_ANSWER_ENDPOINT))
                        .addWebhook(Webhook.Type.EVENT, buildWebhook(VOICE_EVENT_ENDPOINT))
                        .build()
                );

            var application = ac.updateApplication(builder.build());
            assert application != null;
        }
        catch (ApplicationResponseException ex) {
            logger.warning("Failed to update application "+appIdStr+": "+ex.getMessage());
            throw ex;
        }
    }
}
