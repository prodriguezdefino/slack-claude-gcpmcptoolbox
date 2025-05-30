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
package org.example.gcp.slack.claude.common;

import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.request.Request;
import com.slack.api.bolt.request.RequestHeaders;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.util.SlackRequestParser;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.AppMentionEvent;
import com.slack.api.model.event.Event;
import com.slack.api.model.event.MessageChangedEvent;
import com.slack.api.model.event.MessageEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** */
public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

  private Utils() {}

  public static <K, V> Map<K, List<V>> toMultiMap(MultiValueMap<K, V> multivalueMap) {
    return multivalueMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public static Request<?> parseSlackRequest(
      SlackRequestParser requestParser, ServerRequest request, String body) {
    return requestParser.parse(
        SlackRequestParser.HttpRequest.builder()
            .requestUri(request.requestPath().toString())
            .requestBody(body)
            .queryString(toMultiMap(request.queryParams()))
            .remoteAddress(
                request.headers().header("X-Forwarded-For").stream().findFirst().orElse(""))
            .headers(new RequestHeaders(toMultiMap(request.headers().asHttpHeaders())))
            .build());
  }

  public static Mono<Response> processSlackRequest(App slackApp, Request<?> slackRequest) {
    return Mono.fromCallable(
            () -> {
              try {
                return slackApp.run(slackRequest);
              } catch (Exception ex) {
                throw new RuntimeException("Problems processing slack application request", ex);
              }
            })
        .subscribeOn(Schedulers.boundedElastic());
  }

  public static void sendErrorToSlack(EventContext ctx, Event event, String errorMessage) {
    try {
      ctx.client()
          .chatPostMessage(
              r -> r.channel(channel(event)).threadTs(threadTs(event)).text(errorMessage));
      LOG.info("Sent error message to Slack: {}", errorMessage);
    } catch (IOException | SlackApiException e) {
      LOG.error("Failed to send error message to Slack: {}", e.getMessage(), e);
    }
  }

  public static String toText(List<String> bufferedResponse) {
    return bufferedResponse.stream().collect(Collectors.joining());
  }

  public static String removeMention(String text) {
    return text.replaceFirst("<@.*?>", "").trim();
  }

  public static String threadTs(Event event) {
    return switch (event) {
      case AppMentionEvent mention ->
          Optional.ofNullable(mention.getThreadTs()).orElse(mention.getTs());
      case MessageEvent message ->
          Optional.ofNullable(message.getThreadTs()).orElse(message.getTs());
      case MessageChangedEvent change ->
          Optional.ofNullable(change.getMessage().getThreadTs())
              .orElse(change.getMessage().getTs());
      default ->
          throw new IllegalArgumentException(
              "Retrieve thread failed. Event type is not supported: " + event.getType());
    };
  }

  public static String channel(Event event) {
    return switch (event) {
      case AppMentionEvent mention -> mention.getChannel();
      case MessageEvent message -> message.getChannel();
      case MessageChangedEvent change -> change.getChannel();
      default ->
          throw new IllegalArgumentException(
              "Retrieve channel failed. Event type is not supported: " + event.getType());
    };
  }

  public static Message toMessage(String userId, String botId, String text) {
    if (userId.equals(botId)) return new AssistantMessage(text);
    return new UserMessage(text);
  }

  public static String exceptionMessage(Throwable ex) {
    return Optional.ofNullable(NestedExceptionUtils.getRootCause(ex))
        .map(Throwable::getMessage)
        .orElse(ex.getMessage());
  }

  public static String errorMessage(Throwable ex) {
    return """
        Problems executing the task, you can try retrying it.
        Detailed cause:  """
        + exceptionMessage(ex);
  }

  public static List<String> separateNewlines(String text) {
    var delimiter = "<DELIMITER/>";
    return Arrays.asList(text.replace("\n", "\n" + delimiter).split(delimiter));
  }
}
