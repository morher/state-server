package net.morher.state;

import java.net.URI;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.javalin.Javalin;
import io.javalin.http.Context;
import kotlin.jvm.Synchronized;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StateServer {
  private final int port;
  private final StateHandler stateHandler;
  private final WebSocketServer wsServer;

  public static void main(String[] args) { new StateServer(8056, Path.of("./")).run(); }

  public StateServer(int port, Path dataPath) {
    this.port = port;
    this.stateHandler = new StateHandler(dataPath);
    this.wsServer = new WebSocketServer(stateHandler);
    this.stateHandler.getListeners().add(wsServer);
  }

  public void run() {
    Javalin.create()
        .get("/state/{projectId}/{stateId}", this::getState)
        .put("/state/{projectId}/{stateId}", this::putState)
        .patch("/state/{projectId}/{stateId}", this::patchState)
        .ws("/state/{projectId}", ws -> {
          ws.onConnect(wsServer);
          ws.onError(wsServer);
          ws.onMessage(wsServer);
          ws.onClose(wsServer);
        })
        .start(port);
  }

  @Synchronized
  public void getState(Context ctx) throws Exception {
    String projectId = ctx.pathParam("projectId");
    String stateId = ctx.pathParam("stateId");

    ctx.json(stateHandler.getState(projectId, stateId));
  }

  @Synchronized
  public void putState(Context ctx) throws Exception {
    String projectId = ctx.pathParam("projectId");
    String stateId = ctx.pathParam("stateId");

    stateHandler.updateState(projectId, stateId, ctx.bodyAsClass(JsonNode.class), true);
  }

  @Synchronized
  public void patchState(Context ctx) throws Exception {
    String projectId = ctx.pathParam("projectId");
    String stateId = ctx.pathParam("stateId");

    stateHandler.updateState(projectId, stateId, ctx.bodyAsClass(JsonNode.class), false);
  }
}
