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

import com.vonage.client.messages.sms.SmsTextRequest;
import com.vonage.client.verify2.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@Controller
public class VerifyController extends VonageController {
	static final String
			VERIFY_START_TEMPLATE = "verify_start",
			VERIFY_RESULT_TEMPLATE = "verify_result";

	private final Map<UUID, String> successfulVerifications = new LinkedHashMap<>();

	protected Verify2Client getVerifyClient() {
		return getVonageClient().getVerify2Client();
	}

	@GetMapping("/verify")
	public String verificationRequestForm(Model model) {
		var verifyParams = new VerifyParams();
		verifyParams.channelOptions = Channel.values();
		verifyParams.brand = "Vonage";
		verifyParams.toNumber = System.getenv("TO_NUMBER");
		verifyParams.fromNumber = System.getenv("VONAGE_FROM_NUMBER");
		verifyParams.toEmail = System.getenv("TO_EMAIL");
		verifyParams.fromEmail = System.getenv("VONAGE_FROM_EMAIL");
		model.addAttribute("verifyParams", verifyParams);
		return VERIFY_START_TEMPLATE;
	}

	@PostMapping("/postVerificationRequest")
	public String postVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			var builder = VerificationRequest.builder().brand(verifyParams.brand);
			String toNumber = verifyParams.toNumber, toEmail = verifyParams.toEmail,
					fromNumber = verifyParams.fromNumber, fromEmail = verifyParams.fromEmail;

			if (fromNumber != null && fromNumber.isBlank()) {
				fromNumber = null;
			}
			if (fromEmail != null && fromEmail.isBlank()) {
				fromEmail = null;
			}

			var channel = Channel.valueOf(verifyParams.selectedChannel);
			boolean codeless = (channel == Channel.SILENT_AUTH || channel == Channel.WHATSAPP_INTERACTIVE);
			verifyParams.codeless = codeless;

			if (toNumber.matches("0+") || "test@example.com".equals(toEmail)) {
				verifyParams.requestId = UUID.randomUUID();
				if (channel == Channel.SILENT_AUTH) {
					verifyParams.checkUrl = URI.create("https://api.vonage.com/v2/verify/" +
							verifyParams.requestId + "silent-auth/redirect"
					);
				}
			}
			else {
				builder.addWorkflow(switch (channel) {
					case EMAIL -> new EmailWorkflow(toEmail, fromEmail);
					case SMS -> SmsWorkflow.builder(toNumber).from(fromNumber).build();
					case VOICE -> new VoiceWorkflow(toNumber);
					case WHATSAPP -> new WhatsappWorkflow(toNumber, fromNumber);
					case WHATSAPP_INTERACTIVE -> new WhatsappCodelessWorkflow(toNumber, fromNumber);
					case SILENT_AUTH -> new SilentAuthWorkflow(
							toNumber, true,
							getServerUrl().resolve("/verify/saComplete").toString());
				});

				if (!codeless && verifyParams.codeLength != null) {
					builder.codeLength(verifyParams.codeLength);
				}
				var request = builder.build();
				assert request.isCodeless() == codeless;
				var response = getVerifyClient().sendVerification(request);
				verifyParams.requestId = response.getRequestId();
				verifyParams.checkUrl = response.getCheckUrl();
				if (channel == Channel.SILENT_AUTH) {
					var messageResponse = getVonageClient().getMessagesClient().sendMessage(
							SmsTextRequest.builder()
								.to(toNumber)
								.from(request.getBrand())
								.text("Follow this on mobile data: "+response.getCheckUrl())
								.build()
					);
					assert messageResponse != null && messageResponse.getMessageUuid() != null;
				}
			}
			model.addAttribute("verifyParams", verifyParams);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@PostMapping("/checkVerificationRequest")
	public String checkVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			String result = "Code matched. Verification successful.";
			if (verifyParams.codeless || (verifyParams.checkUrl != null && !verifyParams.checkUrl.toString().isEmpty()) || verifyParams.userCode == null) {
				String code = successfulVerifications.remove(verifyParams.requestId);
				if (code == null) synchronized (successfulVerifications) {
					try {
						successfulVerifications.wait(2000);
						if ((code = successfulVerifications.remove(verifyParams.requestId)) == null) {
							result = "Verification failed.";
						}
					}
					catch (InterruptedException ie) {
						// Continue
					}
				}
				verifyParams.userCode = code;
			}
			else {
				try {
					getVerifyClient().checkVerificationCode(verifyParams.requestId, verifyParams.userCode);
					result = "Code matched. Verification successful.";
				}
				catch (VerifyResponseException ex) {
					result = ex.getMessage();
				}
			}
			model.addAttribute("result", result);
			model.addAttribute("verifyParams", verifyParams);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@PostMapping("/cancelVerificationRequest")
	public String cancelVerificationRequest(@ModelAttribute VerifyParams verifyParams, Model model) {
		try {
			String result;
			try {
				getVerifyClient().cancelVerification(verifyParams.requestId);
				result = "Verification workflow aborted.";
			}
			catch (VerifyResponseException ex) {
				result = ex.getMessage();
			}
			model.addAttribute("result", result);
			return VERIFY_RESULT_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@PostMapping(ApplicationConfiguration.VERIFY_STATUS_ENDPOINT)
	public String eventsWebhook(@RequestBody VerificationCallback payload) {
		System.out.println(payload.toJson());
		return standardWebhookResponse();
	}

	@ResponseBody
	@GetMapping("/verify/saComplete")
	public String completeRegistrationExternal() {
		return """
			<!DOCTYPE html>
			<html lang="en">
			<head>
				<meta charset="UTF-8">
				<title>Convert Fragment</title>
				<script type="text/javascript">
					window.onload = function() {
						if (window.location.hash) {
							window.location.replace(window.location.href.replace('#', '/final?'));
						}
					};
				</script>
			</head>
			<body>
			<p>Redirecting...</p>
			</body>
			</html>
		""";
	}

	@ResponseBody
	@GetMapping("/verify/saComplete/final")
	public String completeRegistrationInternal(
			@RequestParam("request_id") UUID requestId,
			@RequestParam(required = false) String code,
			@RequestParam(value = "error_description", required = false) String errorText
	) {
		String reason;
		if (code != null) {
			var check = getVerifyClient().checkVerificationCode(requestId, code);
			var status = check.getStatus();
			if (status == VerificationStatus.COMPLETED) synchronized (successfulVerifications) {
				successfulVerifications.put(requestId, code);
				successfulVerifications.notify();
				return "<h1>Registration successful!</h1>";
			}
			else {
				reason = status.toString();
			}
		}
		else {
			reason = errorText;
		}
		return "<h1>Registration failed: "+reason+"</h1>";
	}

	public static class VerifyParams {
		private boolean codeless;
		private UUID requestId;
		private URI checkUrl;
		private String brand, selectedChannel, userCode,
				toNumber, toEmail, fromNumber, fromEmail;
		private Channel[] channelOptions;
		private Integer codeLength;

		public boolean isCodeless() {
			return codeless;
		}

		public void setCodeless(boolean codeless) {
			this.codeless = codeless;
		}

		public UUID getRequestId() {
			return requestId;
		}

		public void setRequestId(UUID requestId) {
			this.requestId = requestId;
		}

		public URI getCheckUrl() {
			return checkUrl;
		}

		public void setCheckUrl(URI checkUrl) {
			this.checkUrl = checkUrl;
		}

		public String getBrand() {
			return brand;
		}

		public void setBrand(String brand) {
			this.brand = brand;
		}

		public String getSelectedChannel() {
			return selectedChannel;
		}

		public void setSelectedChannel(String selectedChannel) {
			this.selectedChannel = selectedChannel;
		}

		public String getUserCode() {
			return userCode;
		}

		public void setUserCode(String userCode) {
			this.userCode = userCode;
		}

		public String getToNumber() {
			return toNumber;
		}

		public void setToNumber(String toNumber) {
			this.toNumber = toNumber;
		}

		public String getToEmail() {
			return toEmail;
		}

		public void setToEmail(String toEmail) {
			this.toEmail = toEmail;
		}

		public String getFromNumber() {
			return fromNumber;
		}

		public void setFromNumber(String fromNumber) {
			this.fromNumber = fromNumber;
		}

		public String getFromEmail() {
			return fromEmail;
		}

		public void setFromEmail(String fromEmail) {
			this.fromEmail = fromEmail;
		}

		public Channel[] getChannelOptions() {
			return channelOptions;
		}

		public void setChannelOptions(Channel[] channelOptions) {
			this.channelOptions = channelOptions;
		}

		public Integer getCodeLength() {
			return codeLength;
		}

		public void setCodeLength(Integer codeLength) {
			this.codeLength = codeLength;
		}
	}
}
