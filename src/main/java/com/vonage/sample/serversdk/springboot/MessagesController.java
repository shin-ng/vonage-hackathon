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

import com.vonage.client.messages.*;
import static com.vonage.client.messages.MessageType.*;
import com.vonage.client.messages.messenger.*;
import com.vonage.client.messages.mms.*;
import com.vonage.client.messages.rcs.*;
import com.vonage.client.messages.sms.*;
import com.vonage.client.messages.viber.*;
import com.vonage.client.messages.whatsapp.*;
import com.vonage.client.voice.Call;
import com.vonage.client.voice.MachineDetection;
import com.vonage.client.voice.ncco.TalkAction;
import com.vonage.sample.serversdk.springboot.VoiceController.VoiceCallParams;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public final class MessagesController extends VonageController {
	static final String
			MESSAGES_TEMPLATE = "messages",
			COMMAND_CENTRE_TEMPLATE = "command_centre",
			MESSAGE_PARAMS_NAME = "messageParams";

	private final Map<UUID, InboundMessage> inboundMessages = new HashMap<>();
	private final Map<UUID, MessageStatus> messageStatuses = new HashMap<>();
	private final List<String> coordinates = new ArrayList<>();

	private MessageRequest applyCommonParams(MessageRequest.Builder<?, ?> builder, MessageParams params) {
		return builder.from(params.from).to(params.to).build();
	}

	private static String nullifyIfEmpty(String param) {
		return param == null || param.isBlank() ? null : param;
	}

	MessageRequest buildMessage(MessageParams params) {
		String url = nullifyIfEmpty(params.url), text = nullifyIfEmpty(params.text);
		var channel = Channel.valueOf(params.selectedChannel);
		var messageType = MessageType.valueOf(params.selectedType);

		MessageRequest.Builder<?, ?> builder = switch (channel) {
			case SMS -> SmsTextRequest.builder().text(text);
			case WHATSAPP -> switch (messageType) {
				case TEXT -> WhatsappTextRequest.builder().text(text);
				case AUDIO -> WhatsappAudioRequest.builder().url(url);
				case IMAGE -> WhatsappImageRequest.builder().url(url).caption(text);
				case VIDEO -> WhatsappVideoRequest.builder().url(url).caption(text);
				case FILE -> WhatsappFileRequest.builder().url(url).caption(text);
				case STICKER -> WhatsappStickerRequest.builder().url(url).id(text);
				case REACTION -> text == null || text.isBlank() ?
						WhatsappReactionRequest.builder().unreact() :
						WhatsappReactionRequest.builder().reaction(text);
				case LOCATION -> WhatsappLocationRequest.builder()
                        .name(params.text).address(params.address)
                        .latitude(params.latitude).longitude(params.longitude);
				default -> throw new IllegalStateException();
			};
			case MMS -> switch (messageType) {
				case VCARD -> MmsVcardRequest.builder().url(url).caption(text);
				case AUDIO -> MmsAudioRequest.builder().url(url).caption(text);
				case IMAGE -> MmsImageRequest.builder().url(url).caption(text);
				case VIDEO -> MmsVideoRequest.builder().url(url).caption(text);
				default -> throw new IllegalStateException();
			};
			case MESSENGER -> switch (messageType) {
				case TEXT -> MessengerTextRequest.builder().text(text);
				case IMAGE -> MessengerImageRequest.builder().url(url);
				case AUDIO -> MessengerAudioRequest.builder().url(url);
				case VIDEO -> MessengerVideoRequest.builder().url(url);
				case FILE -> MessengerFileRequest.builder().url(url);
				default -> throw new IllegalStateException();
			};
			case VIBER -> switch (messageType) {
				case TEXT -> ViberTextRequest.builder().text(text);
				case IMAGE -> ViberImageRequest.builder().url(url);
				case FILE -> ViberFileRequest.builder().url(url);
				default -> throw new IllegalStateException();
			};
			case RCS -> switch (messageType) {
				case TEXT -> RcsTextRequest.builder().text(text);
				case IMAGE -> RcsImageRequest.builder().url(url);
				case FILE -> RcsFileRequest.builder().url(url);
				case VIDEO -> RcsVideoRequest.builder().url(url);
				default -> throw new IllegalStateException();
			};
		};
		return applyCommonParams(builder, params);
	}

	@ResponseBody
	@GetMapping("getSandboxNumbers")
	public String getSandboxNumbers() {
		return "{\"" +
				Channel.WHATSAPP.name()+"\":\""+System.getenv("VONAGE_WHATSAPP_NUMBER") +
				"\",\""+Channel.VIBER.name()+"\":\""+System.getenv("VONAGE_VIBER_ID") +
				"\",\""+Channel.MESSENGER.name()+"\":\""+System.getenv("VONAGE_MESSENGER_ID") +
				"\"}";
	}

	@ResponseBody
	@GetMapping("getMessageTypes")
	public String getMessageTypes(@RequestParam String channel) {
		var channelEnum = Channel.valueOf(channel);
		return "[" + channelEnum.getSupportedOutboundMessageTypes().stream()
				.filter(mt -> mt != TEMPLATE && mt != CUSTOM && mt != REACTION &&
						(channelEnum != Channel.VIBER || mt != VIDEO)
				)
				.map(mt -> '"'+mt.name()+'"')
				.collect(Collectors.joining(",")) + "]";
	}

	private String setAndReturnTemplate(Model model, MessageParams messageParams) {
		model.addAttribute(MESSAGE_PARAMS_NAME, messageParams);
		return MESSAGES_TEMPLATE;
	}

	@GetMapping("/messages")
	public String messageStart(Model model) {
		var messageParams = new MessageParams();
		messageParams.to = System.getenv("TO_NUMBER");
		messageParams.text = "Hello, World!";
		return setAndReturnTemplate(model, messageParams);
	}

	@GetMapping("/commandCentre")
	public String commandCentre(Model model) {
		var messageParams = new MessageParams();
		messageParams.to = System.getenv("TO_NUMBER");
		messageParams.from = System.getenv("VONAGE_WHATSAPP_NUMBER");
		messageParams.text = "Aliens Sighted!";
		messageParams.selectedChannel = "WHATSAPP";
		messageParams.sandbox = true;
		messageParams.selectedType = "LOCATION";
		messageParams.speech = "Proceed with 2 teams of 5";
        synchronized (coordinates) {
            if (!coordinates.isEmpty()) {
                var coord = coordinates.remove(0).split(",");
                messageParams.latitude = Double.parseDouble(coord[0]);
                messageParams.longitude = Double.parseDouble(coord[1]);
            }
        }
		model.addAttribute(MESSAGE_PARAMS_NAME, messageParams);
		return COMMAND_CENTRE_TEMPLATE;
	}

	@PostMapping("/sendLocationMessage")
	public String sendLocationMessage(@ModelAttribute(MESSAGE_PARAMS_NAME) MessageParams messageParams, Model model) {
		try {
			var messageRequest = buildMessage(messageParams);
			var client = getVonageClient().getMessagesClient();
			if (messageParams.sandbox) {
				client.useSandboxEndpoint();
			}
			else {
				client.useRegularEndpoint();
			}
			var response = client.sendMessage(messageRequest);
			messageParams.messageId = response.getMessageUuid();
            model.addAttribute(MESSAGE_PARAMS_NAME, messageParams);
            return COMMAND_CENTRE_TEMPLATE;
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

    @PostMapping("/sendMessage")
	public String sendMessage(@ModelAttribute(MESSAGE_PARAMS_NAME) MessageParams messageParams, Model model) {
		try {
			var messageRequest = buildMessage(messageParams);
			var client = getVonageClient().getMessagesClient();
			if (messageParams.sandbox) {
				client.useSandboxEndpoint();
			}
			else {
				client.useRegularEndpoint();
			}
			var response = client.sendMessage(messageRequest);
			messageParams.messageId = response.getMessageUuid();
			return setAndReturnTemplate(model, messageParams);
		}
		catch (Exception ex) {
			return errorTemplate(model, ex);
		}
	}

	@ResponseBody
	@GetMapping(ApplicationConfiguration.INBOUND_MESSAGE_ENDPOINT)
    public String inboundWebhookGet(@RequestParam Map<String, String> params) {
        System.out.println(params.keySet());
        System.out.println(params.values());
        var text = params.get("text");
        if (text.startsWith("coord:")) {
            var coord = text.substring(6).split(",");
            System.out.println("coord: " + coord[0] + "," + coord[1]);
            synchronized (coordinates) {
                coordinates.add(text.substring(6));
            }
        }
        return standardWebhookResponse();
	}

	@ResponseBody
	@PostMapping(ApplicationConfiguration.INBOUND_MESSAGE_ENDPOINT)
	public String inboundWebhook(@RequestBody InboundMessage payload) {
		synchronized (inboundMessages) {
			inboundMessages.put(payload.getMessageUuid(), payload);
			inboundMessages.notify();
		}
		return standardWebhookResponse();
	}

	@ResponseBody
	@PostMapping(ApplicationConfiguration.MESSAGE_STATUS_ENDPOINT)
	public String statusWebhook(@RequestBody MessageStatus payload) {
		synchronized (messageStatuses) {
			messageStatuses.put(payload.getMessageUuid(), payload);
			messageStatuses.notify();
		}
		return standardWebhookResponse();
	}

	@ResponseBody
	@GetMapping("getMessageStatusUpdate")
	public String getMessageStatusUpdate(@RequestParam UUID messageId, @RequestParam long timeout) {
		MessageStatus status;
		synchronized (messageStatuses) {
			if ((status = messageStatuses.remove(messageId)) == null) {
				try {
					messageStatuses.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue;
				}
				status = messageStatuses.remove(messageId);
			}
		}
		if (status == null) return "";
		var formatted = status.getStatus().name();
		if (status.getTimestamp() != null) {
			formatted += " at " + formatInstant(status.getTimestamp());
		}
		var usage = status.getUsage();
		if (usage != null) {
			formatted += ", costing "+usage.getCurrency().getSymbol() + usage.getPrice();
		}
		return "{\"text\":\""+formatted+"\"}";
	}

	@ResponseBody
	@GetMapping("getInboundMessage")
	public String getInboundMessage(@RequestParam UUID messageId, @RequestParam long timeout) {
		InboundMessage inbound;
		synchronized (inboundMessages) {
			if ((inbound = inboundMessages.remove(messageId)) == null) {
				try {
					inboundMessages.wait(timeout);
				}
				catch (InterruptedException ie) {
					// Continue;
				}
				inbound = inboundMessages.remove(messageId);
			}
		}
		if (inbound == null) return "";
		var formatted = inbound.getMessageType().name()+" received";
		if (inbound.getTimestamp() != null) {
			formatted += " at " + formatInstant(inbound.getTimestamp());
		}
		var usage = inbound.getUsage();
		if (usage != null) {
			formatted += ", costing "+usage.getCurrency().getSymbol() + usage.getPrice();
		}
		return "{\"text\":\""+formatted+"\"}";
	}

	public static class MessageParams {
		private UUID messageId;
		private boolean sandbox;
		private double latitude, longitude;
		private String from, to, text, url, address, selectedChannel, selectedType, speech;

		public UUID getMessageId() {
			return messageId;
		}

		public void setMessageId(UUID messageId) {
			this.messageId = messageId;
		}

		public boolean isSandbox() {
			return sandbox;
		}

		public void setSandbox(boolean sandbox) {
			this.sandbox = sandbox;
		}

		public double getLatitude() {
			return latitude;
		}

		public void setLatitude(double latitude) {
			this.latitude = latitude;
		}

		public double getLongitude() {
			return longitude;
		}

		public void setLongitude(double longitude) {
			this.longitude = longitude;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getAddress() {
			return address;
		}

		public void setAddress(String address) {
			this.address = address;
		}

		public String getSelectedChannel() {
			return selectedChannel;
		}

		public void setSelectedChannel(String selectedChannel) {
			this.selectedChannel = selectedChannel;
		}

		public String getSelectedType() {
			return selectedType;
		}

		public void setSelectedType(String selectedType) {
			this.selectedType = selectedType;
		}

		public String getSpeech() {
			return speech;
		}

		public void setSpeech(String speech) {
			this.speech = speech;
		}
	}
}
