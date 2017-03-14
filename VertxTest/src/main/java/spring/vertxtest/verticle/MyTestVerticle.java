/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spring.vertxtest.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.apache.logging.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 *
 * @author skinne_w
 */
@Service
public class MyTestVerticle extends AbstractVerticle {

    private final static Logger log = LoggerFactory.getLogger(MyTestVerticle.class);
    
    final Level ACCESS = Level.forName("ACCESS", 450);

    private boolean started;
    
    private int port;
    
    @Value("${webserver.testpath.enabled}")
    private boolean testPathEnabled;
    
    @Value("${webserver.urlpath.test}")
    private String testUrlPath;
    
    @Value("${webserver.filepath.test}")
    private String testFilePath;
    
    @Value("${webserver.caching.enabled}")
    private boolean cachingEnabled;
    
    @Value("${webserver.ssl.enabled}")
    private boolean sslEnabled;

    private BridgeOptions bridgeOptions;
    
    private SockJSHandler sockJsHandler;
    
    private Router router;
    
    private JksOptions sslKeyStoreOptions;
    
    private JksOptions sslTrustStoreOptions;
    
    public MyTestVerticle() {
        this.started = false;
    }
    
    @Override
    public void start(Future<Void> fut) throws Exception {
        log.info("start() -- starting Vertx Verticle with eventbus, API handler, and static file handler");

        // grab the router
        router = getRouter();

        // enable CORS for the router 
        CorsHandler corsHandler = CorsHandler.create("*");  //Wildcard(*) not allowed if allowCredentials is true
        corsHandler.allowedMethod(HttpMethod.OPTIONS);
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.PUT);
        corsHandler.allowedMethod(HttpMethod.DELETE);
        corsHandler.allowCredentials(false);
        corsHandler.allowedHeader("Access-Control-Request-Method");
        corsHandler.allowedHeader("Access-Control-Allow-Method");
        corsHandler.allowedHeader("Access-Control-Allow-Credentials");
        corsHandler.allowedHeader("Access-Control-Allow-Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Headers");
        corsHandler.allowedHeader("Content-Type");

        // enable handling of body
        router.route().handler(BodyHandler.create());
        router.route().handler(corsHandler);
        router.route().handler(this::handleAccessLogging);

        // publish a payload to provided eventbus destination
        router.post("/api/eventbus/publish/:destination").handler(this::publish);

        // open up all for outbound and inbound traffic
        bridgeOptions = new BridgeOptions();
        bridgeOptions.addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"));
        bridgeOptions.addInboundPermitted(new PermittedOptions().setAddressRegex(".*"));
//        sockJsHandler = SockJSHandler.create(vertx).bridge(bridgeOptions);   
         sockJsHandler = SockJSHandler.create(vertx);
         sockJsHandler.bridge(bridgeOptions, be -> {
            try {
                if (be.type() == BridgeEventType.SOCKET_CREATED) {
                    handleSocketOpenEvent(be);
                }
                else if(be.type() ==BridgeEventType.REGISTER) {
                    handleRegisterEvent(be);
                }
                else if(be.type() ==BridgeEventType.UNREGISTER) {
                    handleUnregisterEvent(be);
                }
                else if(be.type() ==BridgeEventType.SOCKET_CLOSED) {
                    handleSocketCloseEvent(be);
                }
            } catch (Exception e) {

            } finally {
                be.complete(true);
            }
        });
        router.route("/eventbus/*").handler(sockJsHandler);
        
        if(testPathEnabled){
            router.route("/" + testUrlPath + "/*").handler(StaticHandler.create(testFilePath).setCachingEnabled(cachingEnabled));
        }
        
        // create periodic task, pushing all current EventBusRegistrations
        vertx.setPeriodic(1000, handler -> {
            JsonObject obj =new JsonObject();
            obj.put("testMessage", "Periodic test message from server...");
            vertx.eventBus().publish("heartbeat-test", Json.encodePrettily(obj));
        });
        
        EventBus eb = vertx.eventBus();
        eb.consumer("client-test", message -> {
            log.info("Received message from client: " + Json.encodePrettily(message.body()) + " at " + System.currentTimeMillis());
        });
        
        HttpServerOptions httpOptions = new HttpServerOptions();
        if(sslEnabled){
                httpOptions.setSsl(true);
                httpOptions.setKeyStoreOptions(sslKeyStoreOptions);
        }
        
        log.info("starting web server on port: " + port);
        vertx
                .createHttpServer(httpOptions)
                .requestHandler(router::accept).listen(
                port,
                result -> {
                    if (result.succeeded()) {
                        setStarted(true);
                        log.info("Server started and ready to accept requests");
                        fut.complete();
                    } else {
                        setStarted(false);
                        fut.fail(result.cause());
                    }
                }
        );
    }
    
    private void handleSocketOpenEvent(BridgeEvent be){
        String host =be.socket().remoteAddress().toString();
        String localAddress = be.socket().localAddress().toString();
        log.info("Socket connection opened! Host: " + host + " Local address: " + localAddress);
    }
    
    private void handleRegisterEvent(BridgeEvent be){
        String host =be.socket().remoteAddress().toString();
        String localAddress = be.socket().localAddress().toString();
        String address = be.getRawMessage().getString("address").trim();
        log.info("Eventbus register event! Address: " + address + " Host: " + host + " Local address: " + localAddress);
    }
    
    private void handleUnregisterEvent(BridgeEvent be){
        String host =be.socket().remoteAddress().toString();
        String localAddress = be.socket().localAddress().toString();
        String address = be.getRawMessage().getString("address").trim();
        log.info("Eventbus unregister event! Address: " + address + " Host: " + host + " Local address: " + localAddress);
    }
    
    private void handleSocketCloseEvent(BridgeEvent be){
        String host =be.socket().remoteAddress().toString();
        String localAddress = be.socket().localAddress().toString();
        log.info("Socket connection closed! Host: " + host + " Local address: " + localAddress);
    }
    
    //Method handles logging at custom level for access logging to files
    private void handleAccessLogging(RoutingContext routingContext){
        Marker accessMarker = MarkerFactory.getMarker("ACCESS");
        
        if(routingContext.normalisedPath().contains("/api")){
            log.info(accessMarker, "Api access log: request= " + routingContext.normalisedPath() + " source=" + routingContext.request().remoteAddress());
        }
        else{
            log.info(accessMarker, "Web access log: path= " + routingContext.normalisedPath() + " source= " + routingContext.request().remoteAddress());
        }

        routingContext.next();
    }

    /**
     * Accept a payload (anything) and publish to the provided destination
     *
     * @param routingContext
     */
    private void publish(RoutingContext routingContext) {
        String destination = routingContext.request().getParam("destination");
        String payload = routingContext.getBodyAsString();
        if ((destination == null) || (payload == null)) {
            Exception e = new Exception("Missing arguments");
            routingContext.response().setStatusCode(406);
            routingContext.fail(e);
        } else {
            log.info("API Call -> Publishing to destination: " + destination + " payload: " + payload);
            vertx.eventBus().publish(destination, payload);
            routingContext
                    .response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(payload);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    public Router getRouter(){
        if(router == null){
            router = Router.router(vertx);
        }
        return router;
    }
    
    public void setRouter(Router router){
        this.router = router;
    }

    public void setSslOptions(JksOptions keyStoreOptions, JksOptions trustStoreOptions) {
        this.sslKeyStoreOptions = keyStoreOptions;
        this.sslTrustStoreOptions = trustStoreOptions;
    }
}
