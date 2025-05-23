/*
 * Copyright (C) 2025 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.example.gcp.slack.claude.handlers;

import static org.example.gcp.slack.claude.common.Utils.extractResponse;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.event.AppMentionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** */
@Component
public class SlackMention {
  private static final Logger LOG = LoggerFactory.getLogger(SlackMention.class);

  private final ClaudeChat claude;
  private final SlackSend slack;

  public SlackMention(ClaudeChat claude, SlackSend send) {
    this.claude = claude;
    this.slack = send;
  }

  public Response handleMentionEvent(EventsApiPayload<AppMentionEvent> payload, EventContext ctx) {
    AppMentionEvent event = payload.getEvent();
    String userMessageText = event.getText().replaceFirst("<@.*?>", "").trim(); // Remove mention
    String channelId = event.getChannel();
    String threadTs =
        event.getThreadTs() != null
            ? event.getThreadTs()
            : event.getTs(); // Use thread_ts or message_ts
    String historyKey = channelId + "-" + threadTs;
    LOG.info(
        "Received app_mention event from user {} in channel {}: {}",
        event.getUser(),
        channelId,
        userMessageText);

    slack
        .sendResponse(ctx, event, historyKey, "Coming up with a response...")
        .thenCompose(__ -> claude.generate(userMessageText))
        .thenCompose(
            chatResponse ->
                slack.sendResponse(ctx, event, historyKey, extractResponse(chatResponse)))
        .thenAccept(__ -> LOG.info("Sent both responses to Slack."));

    return ctx.ack(); // Acknowledge Slack event immediately
  }
}
