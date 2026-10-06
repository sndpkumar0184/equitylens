package com.equitylens.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
@ConditionalOnProperty(name="equitylens.ai.provider",havingValue="local",matchIfMissing=true)
public class LocalLLMProvider implements AIProvider {
    private final ObjectMapper mapper;
    private final String model;
    private final URI endpoint;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public LocalLLMProvider(ObjectMapper mapper,@Value("${equitylens.ai.local.url:http://127.0.0.1:11434}")String url,
            @Value("${equitylens.ai.local.model:qwen2.5:7b}")String model) {
        this.mapper=mapper;this.model=model;
        URI base=URI.create(url);
        if(!Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null)throw new IllegalArgumentException("Invalid AI runtime configuration");
        endpoint=URI.create(url.replaceAll("/$","")+"/api/chat");
    }
    @Override public Message complete(List<Message> messages,List<ToolDefinition> tools) {
        var serialized=messages.stream().map(m->{
            var row=new LinkedHashMap<String,Object>();row.put("role",m.role());row.put("content",m.content());
            if(m.toolName()!=null)row.put("tool_name",m.toolName());
            if(!m.toolCalls().isEmpty())row.put("tool_calls",m.toolCalls().stream().map(c->Map.of("function",Map.of("name",c.name(),"arguments",c.arguments()))).toList());
            return row;
        }).toList();
        var functions=tools.stream().map(t->Map.of("type","function","function",Map.of("name",t.name(),"description",t.description(),"parameters",t.parameters()))).toList();
        try {
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(90)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("model",model,"messages",serialized,"tools",functions,
                            "stream",false,"options",Map.of("temperature",0.1,"num_predict",2048,"num_ctx",32768))))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try(var body=response.body()){bytes=body.readNBytes(131073);}
            if(response.statusCode()!=200||bytes.length>131072)throw unavailable();
            var root=mapper.readTree(bytes);var node=root.path("message");
            if(!node.isObject()||!node.path("role").asText().equals("assistant"))throw unavailable();
            var calls=new ArrayList<ToolCall>();
            for(var call:node.path("tool_calls")) {
                var f=call.path("function");if(!f.path("arguments").isObject()||!f.path("name").isString())throw unavailable();
                calls.add(new ToolCall(f.path("name").asText(),mapper.convertValue(f.path("arguments"),new TypeReference<Map<String,Object>>(){})));
            }
            String content=node.path("content").asText("");
            // Some local model templates emit a complete JSON function envelope as text.
            // Accept only that exact shape and a discovered tool name; never extract commands from prose.
            if(calls.isEmpty() && content.trim().startsWith("{")) {
                try {
                    var envelope=mapper.readTree(content);
                    String name=envelope.path("name").asText("");
                    if(envelope.isObject() && envelope.size()==2 && envelope.path("arguments").isObject()
                            && tools.stream().anyMatch(t->t.name().equals(name))) {
                        calls.add(new ToolCall(name,mapper.convertValue(envelope.path("arguments"),new TypeReference<Map<String,Object>>(){})));
                        content="";
                    }
                } catch(RuntimeException ignored) { /* Ordinary answer text, not a tool request. */ }
            }
            return new Message("assistant",content,calls,null);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw unavailable();}
        catch(Exception ex){throw unavailable();}
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Local AI is unavailable. Start Ollama and install the configured tool-capable model (see docs/ai-assistant.md).");}
}
