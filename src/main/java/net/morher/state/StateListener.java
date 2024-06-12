package net.morher.state;

import com.fasterxml.jackson.databind.JsonNode;

public interface StateListener {
    void onStateUpdate(String projectId, String stateId, JsonNode delta, JsonNode full);
}
