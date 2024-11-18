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

import com.vonage.client.voice.*;
import com.vonage.client.voice.ncco.TalkAction;
import com.vonage.sample.serversdk.springboot.MessagesController.MessageParams;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class VoiceController extends VonageController {
	static final String
			VOICE_TEMPLATE = "voice",
			VOICE_CALL_PARAMS_NAME = "voiceCallParams";

	private final Map<String, EventWebhook> callEvents = new HashMap<>();

	protected VoiceClient getVoiceClient() {
		return getVonageClient().getVoiceClient();
	}

	@ResponseBody
	@GetMapping("getSpokenLanguages")
	public String getSpokenLanguages() throws Exception {
		return "["+ Arrays.stream(TextToSpeechLanguage.values())
				.map(lang -> '"'+lang.name()+'"')
				.collect(Collectors.joining(",")) + "]";
	}

	@GetMapping("/voice")
	public String voiceCall(Model model) {
		var params = new VoiceCallParams();
		params.language = TextToSpeechLanguage.UNITED_KINGDOM_ENGLISH;
		params.tts = "Hello, World!";
		params.toPstn = System.getenv("TO_NUMBER");
		params.premium = true;
		params.ringTimer = 20;
		model.addAttribute(VOICE_CALL_PARAMS_NAME, params);
		return VOICE_TEMPLATE;
	}

	@PostMapping("textToSpeechRequest")
	public String textToSpeechRequest(@ModelAttribute(VOICE_CALL_PARAMS_NAME) VoiceCallParams params, Model model) {
		try {
			var event = getVoiceClient().createCall(Call.builder()
					.machineDetection(MachineDetection.CONTINUE)
					.to(new PhoneEndpoint(params.toPstn))
					.ncco(TalkAction.builder(params.tts)
							.language(params.language)
							.premium(params.premium).build()
					).lengthTimer(25)
					.ringingTimer(params.ringTimer)
					.fromRandomNumber(true).build()
			);
			params.callId = event.getUuid();
			model.addAttribute(VOICE_CALL_PARAMS_NAME, params);
			return VOICE_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

    @PostMapping("sendVoiceInstruction")
	public String sendVoiceInstruction(@ModelAttribute(MessagesController.MESSAGE_PARAMS_NAME) MessageParams messageParams, Model model) {
		var params = new VoiceCallParams();
		params.language = TextToSpeechLanguage.UNITED_KINGDOM_ENGLISH;
		params.tts = messageParams.getSpeech();
		params.toPstn = System.getenv("TO_NUMBER");
		params.premium = true;
		params.ringTimer = 20;

        try {
			var event = getVoiceClient().createCall(Call.builder()
					.machineDetection(MachineDetection.CONTINUE)
					.to(new PhoneEndpoint(params.toPstn))
					.ncco(TalkAction.builder(params.tts)
							.language(params.language)
							.premium(params.premium).build()
					).lengthTimer(25)
					.ringingTimer(params.ringTimer)
					.fromRandomNumber(true).build()
			);
			messageParams.setMessageId(UUID.fromString(event.getUuid()));
			model.addAttribute(MessagesController.MESSAGE_PARAMS_NAME, messageParams);
			return MessagesController.COMMAND_CENTRE_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@GetMapping("getVoiceCallStatusUpdate")
	public String getVoiceCallStatusUpdate(@RequestParam String callId, @RequestParam long timeout) {
		EventWebhook event;
		synchronized (callEvents) {
			if ((event = callEvents.remove(callId)) == null) {
				try {
					callEvents.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue
				}
				event = callEvents.remove(callId);
			}
		}
		if (event == null) return "";
		return "{\"status\":\""+event.getStatus()+"\",\"detail\":\""+event.getDetail()+"\"}";
	}

	@ResponseBody
	@PostMapping(ApplicationConfiguration.VOICE_ANSWER_ENDPOINT)
	public String answerWebhook(@RequestBody String payload) {
		var parsed = AnswerWebhook.fromJson(payload);
		logger.info("Call "+parsed.getUuid()+" answered");
		return standardWebhookResponse();
	}

	@ResponseBody
	@PostMapping(ApplicationConfiguration.VOICE_EVENT_ENDPOINT)
	public String eventWebhook(@RequestBody String payload) {
		var parsed = EventWebhook.fromJson(payload);
		synchronized (callEvents) {
			callEvents.put(parsed.getCallUuid(), parsed);
			callEvents.notify();
		}
		return standardWebhookResponse();
	}

	public static class VoiceCallParams {
		private TextToSpeechLanguage language;
		private String toPstn, tts, callId;
		private boolean premium;
		private Integer ringTimer;

		public TextToSpeechLanguage getLanguage() {
			return language;
		}

		public void setLanguage(TextToSpeechLanguage language) {
			this.language = language;
		}

		public String getToPstn() {
			return toPstn;
		}

		public void setToPstn(String toPstn) {
			this.toPstn = toPstn;
		}

		public String getTts() {
			return tts;
		}

		public void setTts(String tts) {
			this.tts = tts;
		}

		public String getCallId() {
			return callId;
		}

		public void setCallId(String callId) {
			this.callId = callId;
		}

		public boolean isPremium() {
			return premium;
		}

		public void setPremium(boolean premium) {
			this.premium = premium;
		}

		public Integer getRingTimer() {
			return ringTimer;
		}

		public void setRingTimer(Integer ringTimer) {
			this.ringTimer = ringTimer;
		}
	}
}
