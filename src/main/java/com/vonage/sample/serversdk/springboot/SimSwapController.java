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

import com.vonage.client.camara.simswap.SimSwapClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@Controller
public final class SimSwapController extends VonageController {
    private static final String
            SIM_SWAP_TEMPLATE_NAME = "sim_swap",
            SIM_SWAP_PARAMS_NAME = "simSwapParams",
            SIM_SWAP_URL = "/simSwap";

    private SimSwapClient getSimSwapClient() {
        return getVonageClient().getSimSwapClient();
    }

    @GetMapping(SIM_SWAP_URL)
    public String simSwapStart(Model model) {
        var simSwapParams = new SimSwapParams();
        simSwapParams.msisdn = "+990123456";
        model.addAttribute(SIM_SWAP_PARAMS_NAME, simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
    }

    @PostMapping(SIM_SWAP_URL)
    public String simSwapPost(@ModelAttribute SimSwapParams simSwapParams, Model model) {
        if (simSwapParams.msisdn != null) {
            simSwapParams.date = getSimSwapClient().retrieveSimSwapDate(simSwapParams.msisdn);
        }
        model.addAttribute(SIM_SWAP_PARAMS_NAME, simSwapParams);
        return SIM_SWAP_TEMPLATE_NAME;
    }

    public static class SimSwapParams {
        private String msisdn;
        private Instant date;

        public String getMsisdn() {
            return msisdn;
        }

        public void setMsisdn(String msisdn) {
            this.msisdn = msisdn;
        }

        public Instant getDate() {
            return date;
        }

        public void setDate(Instant date) {
            this.date = date;
        }
    }
}
