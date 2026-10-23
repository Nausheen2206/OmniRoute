package com.omniroute.order.api;

import com.omniroute.order.domain.DltMessage;
import com.omniroute.order.service.DltMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dlt")
@Tag(name = "DLT", description = "Dead Letter Topic Inspection & Replay API")
public class DltController {

    private final DltMessageService dltMessageService;

    public DltController(DltMessageService dltMessageService) {
        this.dltMessageService = dltMessageService;
    }

    @GetMapping("/messages")
    @Operation(summary = "List recent dead-lettered messages across all topics")
    public ResponseEntity<List<DltMessage>> getDeadLetterMessages() {
        return ResponseEntity.ok(dltMessageService.getRecentMessages());
    }

    @PostMapping("/replay/{id}")
    @Operation(summary = "Replay a dead-lettered message to its original topic")
    public ResponseEntity<Map<String, Object>> replayMessage(@PathVariable String id) {
        boolean replayed = dltMessageService.replay(id);
        if (!replayed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "DLT message not found or cannot replay", "id", id));
        }
        return ResponseEntity.ok(Map.of(
                "status", "replayed",
                "id", id,
                "message", "Message republished to original topic"
        ));
    }
}
