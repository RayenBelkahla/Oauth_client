package org.example.gateway_api.Implementation.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.gateway_api.Implementation.Components.*;
import org.example.gateway_api.Implementation.Objects.DeviceInfo;
import org.example.gateway_api.Implementation.Objects.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
public class SessionService {
    private final DeviceInfoParser deviceInfoParser;
    private final OAuthSession oAuthSessionService;
    private final DeviceProvisioning deviceProvisioning;
    private final SessionResolver sessionResolver;
    private final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    public SessionService(OAuthSession oAuthSessionService,
                          DeviceProvisioning deviceProvisioning,
                          SessionResolver sessionResolver,
                          DeviceInfoParser deviceInfoParser) {
        this.oAuthSessionService = oAuthSessionService;
        this.deviceProvisioning = deviceProvisioning;
        this.sessionResolver = sessionResolver;
        this.deviceInfoParser = deviceInfoParser;


    }
    public Mono<String> getGwToken() {
        return oAuthSessionService.getGwToken();
    }

    public Mono<Map<String, Object>> getSession(String clientId, ServerWebExchange exchange) {

        return oAuthSessionService.getSession(clientId, exchange);
    }

    public Mono<String> includeDeviceId(ServerWebExchange exchange) {
        return deviceProvisioning.addDeviceId(exchange);
    }
    private final ObjectMapper mapper = new ObjectMapper();

    public Mono<DeviceInfo> parseDeviceInfo(String rawJson) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode jsonNode = mapper.readTree(rawJson);
                if (!jsonNode.hasNonNull(Variables.DEVICE_ID)) {
                    throw new IllegalArgumentException("Missing field: deviceId");
                }
                if (!jsonNode.hasNonNull(Variables.DI_CHANNEL)) {
                    throw new IllegalArgumentException("Missing field: channel");
                }
                String deviceId = jsonNode.get(Variables.DEVICE_ID).asText();
                String platform = jsonNode.hasNonNull("platform")
                        ? jsonNode.get(Variables.PLATFORM).asText()
                        : jsonNode.path(Variables.OS).asText();
                String osVersion = jsonNode.path(Variables.OS_VERSION).asText();
                String model = jsonNode.path(Variables.MODEL).asText();
                String modelVersion = jsonNode.path(Variables.MODEL_VERSION).asText();
                String channel = jsonNode.get(Variables.DI_CHANNEL).asText();

                return new DeviceInfo(deviceId, channel, platform, osVersion, model, modelVersion);
            } catch (Exception e) {
                logger.error("Error parsing device info: {}", e.getMessage());
                return null;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    public Mono<WebSession> getMainSessionAttributes(ServerWebExchange exchange) {
        return sessionResolver.resolveSession(exchange);
    }
    public Mono<DeviceInfo> createDeviceInfo(ServerWebExchange exchange){
        String Channel = exchange.getRequest().getHeaders().getFirst(Variables.CHANNEL);
                String deviceId = String.valueOf(deviceProvisioning.generateDeviceId(exchange));
        return deviceInfoParser.extract(exchange, deviceId, Channel);
    }
    public Mono<DeviceInfo> saveDeviceInfoInSession(DeviceInfo deviceInfo, ServerWebExchange exchange) {
        return exchange.getSession()
                .doOnNext(session -> {
                    if (session.getAttribute("DEVICE-INFO") == null) {
                        session.getAttributes().put("DEVICE-INFO", deviceInfo.toString());
                    }
                })
                .thenReturn(deviceInfo);
    }
    public Mono<Map<String, Object>> getDeviceInfo(ServerWebExchange exchange) {
        return oAuthSessionService.getDeviceInfo(exchange);
    }

}