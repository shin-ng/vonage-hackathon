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

import com.vonage.client.VonageClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import java.net.URI;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.logging.Logger;

public abstract class VonageController {
	static final String ERROR_TEMPLATE = "error";

	protected Logger logger = Logger.getLogger("controller");

	@Autowired
	private ApplicationConfiguration configuration;

	protected VonageClient getVonageClient() {
		return configuration.vonageClient;
	}

	protected URI getServerUrl() {
		return configuration.serverUrl;
	}

	protected String errorTemplate(Model model, Exception ex) {
		model.addAttribute("message", ex.getMessage());
		return ERROR_TEMPLATE;
	}

	protected String standardWebhookResponse() {
		return "OK";
	}

	protected String formatInstant(Instant timestamp) {
		var localTime = ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault());
		return DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).format(localTime);
	}

	protected String formatMoney(String currency, double amount) {
		var formatter = NumberFormat.getCurrencyInstance();
		formatter.setCurrency(Currency.getInstance(currency));
		return formatter.format(amount);
	}
}
