package com.equitylens.ai;

import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;
    public AssistantController(AssistantService assistant){this.assistant=assistant;}
    @PostMapping("/chat") public AssistantService.Response chat(@RequestBody AssistantService.Request request){return assistant.chat(request);}
}
