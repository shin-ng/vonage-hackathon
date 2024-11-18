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

import com.vonage.client.camara.numberverification.NumberVerificationClient;
import static com.vonage.sample.serversdk.springboot.ApplicationConfiguration.NUMBER_VERIFICATION_REDIRECT_ENDPOINT;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import java.net.URI;
import java.util.UUID;

@Controller
public final class NumberVerificationController extends VonageController {
    private static final String
            NUMBER_VERIFICATION_TEMPLATE_NAME = "number_verification",
            NUMBER_VERIFICATION_PARAMS_NAME = "numberVerificationParams",
            NUMBER_VERIFICATION_URL = "/numberVerification";

    private NumberVerificationClient getNumberVerificationClient() {
        return getVonageClient().getNumberVerificationClient();
    }

    @GetMapping(NUMBER_VERIFICATION_URL)
    public String numberVerificationStart(Model model) {
        var numberVerificationParams = new NumberVerificationParams();
        numberVerificationParams.msisdn = "+990123456";
        model.addAttribute(NUMBER_VERIFICATION_PARAMS_NAME, numberVerificationParams);
        return NUMBER_VERIFICATION_TEMPLATE_NAME;
    }

    @PostMapping(NUMBER_VERIFICATION_URL)
    public RedirectView buildVerificationUrl(@ModelAttribute NumberVerificationParams nvParams, Model model) {
        var redirectUrl = getServerUrl().resolve(NUMBER_VERIFICATION_REDIRECT_ENDPOINT);
        try {
            nvParams.url = getNumberVerificationClient().initiateVerification(
                    nvParams.msisdn, redirectUrl, UUID.randomUUID().toString().replace("-", "")
            );
            model.addAttribute(NUMBER_VERIFICATION_PARAMS_NAME, nvParams);
            return new RedirectView(nvParams.url.toString());
        }
        catch (Exception ex) {
            return new RedirectView(ERROR_TEMPLATE);
        }
    }

    @GetMapping(NUMBER_VERIFICATION_REDIRECT_ENDPOINT)
    public String inboundWebhook(@RequestParam String code, @RequestParam(required = false) String state, Model model) {
        System.out.println("Received code '"+code+"' with state '"+state+"'.");
        boolean result = getNumberVerificationClient().verifyNumber(code);
        var nvParams = new NumberVerificationParams();
        nvParams.code = code;
        nvParams.msisdn = state;
        nvParams.state = state;
        nvParams.result = result ? "Number matches." : "Fraudulent!";
        model.addAttribute(NUMBER_VERIFICATION_PARAMS_NAME, nvParams);
        return NUMBER_VERIFICATION_TEMPLATE_NAME;
    }

    public static class NumberVerificationParams {
        private String msisdn, code, state, result;
        private URI url;

        public String getMsisdn() {
            return msisdn;
        }

        public void setMsisdn(String msisdn) {
            this.msisdn = msisdn;
        }

        public URI getUrl() {
            return url;
        }

        public void setUrl(URI url) {
            this.url = url;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }
}
