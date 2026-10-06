package com.equitylens.ai;

import java.util.List;
import java.util.Map;

/** Provider-neutral conversation and tool selection contract. No DB/filesystem access. */
public interface AIProvider {
    record ToolCall(String name, Map<String,Object> arguments) {}
    record Message(String role, String content, List<ToolCall> toolCalls, String toolName) {
        public static Message text(String role,String content){return new Message(role,content,List.of(),null);}
    }
    record ToolDefinition(String name,String description,Map<String,Object> parameters) {}
    Message complete(List<Message> messages,List<ToolDefinition> tools);
}
