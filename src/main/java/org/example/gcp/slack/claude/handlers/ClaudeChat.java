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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** */
@Component
public class ClaudeChat {

  private final ChatClient chatClient;
  private final SystemPromptTemplate systemPrompt;

  public ClaudeChat(ChatClient chatClient, SystemPromptTemplate systemPrompt) {
    this.chatClient = chatClient;
    this.systemPrompt = systemPrompt;
  }

  @Async
  public CompletableFuture<ChatResponse> generate(String message) {
    return CompletableFuture.completedFuture(
        chatClient
            .prompt(new Prompt(List.of(new UserMessage(message), systemPrompt.createMessage())))
            .call()
            .chatResponse());
  }
}
