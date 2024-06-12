package net.morher.state;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import kotlin.jvm.Synchronized;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StateHandler {
    private static final JsonNode DEFAULT_STATE = JsonNodeFactory.instance.objectNode();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory()).setSerializationInclusion(Include.NON_NULL);
    private final Path basePath;
    @Getter private final List<StateListener> listeners = new ArrayList<>();

    @Synchronized
    public JsonNode getState(String projectId, String stateId) {
        return new State(projectId, stateId).read().orElse(DEFAULT_STATE);
    }

    @Synchronized
    public void updateState(String projectId, String stateId, JsonNode body, boolean patch) {
        State state = new State(projectId, stateId);
        if (!state.stateExists()) {
            throw new RuntimeException("Failed to update state");
        }

        Optional<JsonNode> currentState = patch ? state.read() : Optional.empty();
        if (currentState.isPresent()) {
            try {
                ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);
                ObjectReader updater = mapper.readerForUpdating(state.read().orElse(DEFAULT_STATE));
                JsonNode merged = updater.readValue(body);

                state.write(merged);
                listeners.forEach(l -> l.onStateUpdate(projectId, stateId, body, merged));

            } catch (IOException e) {
                throw new RuntimeException("Failed to update state", e);            
            }
        } else {
            state.write(body);
            listeners.forEach(l -> l.onStateUpdate(projectId, stateId, body, body));
        }
    }

    @Synchronized
    public void requestStateUpdate(String projectId, String stateId, StateListener listener) {
        State state = new State(projectId, stateId);
        JsonNode data = state.read().orElse(DEFAULT_STATE);
        listener.onStateUpdate(projectId, stateId, data, data);
    }
    
    @RequiredArgsConstructor
    public class State {
        private final String projectId;
        private final String stateId;

        public Path projectPath() {
            return basePath.resolve(projectId);
        }

        public Path projectMetaFile() {
            return projectPath().resolve("project.yaml");
        }

        public Path statePath() {
            return projectPath().resolve(stateId);
        }

        public boolean stateExists() {
            File stateDir = statePath().toFile();
            return stateDir.exists() && stateDir.isDirectory();
        }

        public Path stateDataFile() {
            return statePath().resolve("state.yaml");
        }

        public Optional<JsonNode> read() {
            Path stateFile = stateDataFile();
            if (stateFile.toFile().exists()) {
                try {
                    JsonNode state = YAML_MAPPER.readValue(stateFile.toFile(), JsonNode.class);
                    return Optional.of(state);
                } catch(Exception e) {
                    log.error("Failed to read state {} / {}", projectId, stateId, e);
                }
            }
            return Optional.empty();
        }

        public void write(JsonNode state) {
            try {
                YAML_MAPPER.writeValue(stateDataFile().toFile(), state);
            
            } catch (IOException e) {
                throw new RuntimeException("Failed to update state", e);
            }    
        }
    }
}
