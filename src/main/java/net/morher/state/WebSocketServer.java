package net.morher.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsCloseHandler;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsConnectHandler;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsErrorContext;
import io.javalin.websocket.WsErrorHandler;
import io.javalin.websocket.WsMessageContext;
import io.javalin.websocket.WsMessageHandler;
import kotlin.jvm.Synchronized;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class WebSocketServer implements WsConnectHandler, WsCloseHandler, WsMessageHandler, WsErrorHandler, StateListener {
    private static final Map<WsContext, User> userMap = new ConcurrentHashMap<>();
    private final StateHandler stateHandler;

    public void handleConnect(WsConnectContext ctx) {
        String projectId = ctx.pathParam("projectId");
        System.out.println("Connect: " + projectId);
        User user = new User(ctx, projectId);
        userMap.put(ctx, user);
        ctx.queryParams("sub").forEach(user::subscribe);
        ctx.enableAutomaticPings(20, TimeUnit.SECONDS);
    }

    public void handleClose(WsCloseContext ctx) {
        System.out.println("Close: " + ctx);
    }

    public void handleMessage(WsMessageContext ctx) {
        System.out.println("Message: " + ctx);

        User user = userMap.get(ctx);
        WsCommand command = ctx.messageAsClass(WsCommand.class);
        user.handle(command);
    }

    public void handleError(WsErrorContext ctx) {
        System.out.println("Error: " + ctx);
    }

    @Override
    public void onStateUpdate(String projectId, String stateId, JsonNode delta, JsonNode full) {
        userMap.values().forEach(u -> u.onStateUpdate(projectId, stateId, delta, full));
    }

    @Data
    public static class WsCommand {
        private final List<String> sub = new ArrayList<>();
    }

    @Data
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class WsData {
        private String projectId;
        private String stateId;
        private JsonNode state;
    }

    @RequiredArgsConstructor
    public class User implements StateListener {
        private final Map<String, Boolean> subscriptions = new HashMap<>();
        private final WsContext ctx;
        private final String projectId;

        @Synchronized
        public void handle(WsCommand command) {
            command.sub.forEach(this::subscribe);
        }

        @Synchronized
        public void subscribe(String stateId) {
            log.info("Client {} subscribed to {}", ctx.host(), stateId);
            subscriptions.put(stateId, true);
            stateHandler.requestStateUpdate(projectId, stateId, this);
        }

        @Synchronized
        public void unsubscribe(String stateId) {
            log.info("Client {} unsubscribed from {}", ctx.host(), stateId);
            subscriptions.remove(stateId);
        }

        @Override
        public void onStateUpdate(String projectId, String stateId, JsonNode delta, JsonNode full) {
            if (this.projectId.equals(projectId) && subscriptions.get(stateId) != null) {
                this.sendState(stateId, delta, full);
            }
        }

        @Synchronized
        public void sendState(String stateId,JsonNode delta, JsonNode full) {
            Boolean isNew = subscriptions.get(stateId);
            if (isNew != null && isNew) {
                ctx.send(new WsData(projectId, stateId, full));
                subscriptions.put(stateId, false);
            } else {
                ctx.send(new WsData(projectId, stateId, delta));
            }
        }

    }
}
