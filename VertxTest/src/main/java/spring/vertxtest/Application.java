/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package spring.vertxtest;

import spring.vertxtest.verticle.MyTestVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 *
 * @author skinne_w
 */
@Configuration
@ComponentScan("spring.vertxtest")
@PropertySource(value = "file:./config/application.properties", ignoreResourceNotFound = false)
@EnableScheduling
public class Application {

    private final static Logger log = LoggerFactory.getLogger(Application.class);
    
    @Autowired
    private MyTestVerticle verticle;
    
    @Value("${webserver.ssl.keystore.path}")
    private String sslKeystorePath;
    
    @Value("${webserver.ssl.keystore.password}")
    private String sslKeystorePassword;
    
    @Value("${webserver.ssl.truststore.path}")
    private String sslTruststorePath;
    
    @Value("${webserver.ssl.truststore.password}")
    private String sslTruststorePassword;
    
    @Value("${webserver.ssl.enabled}")
    private boolean sslEnabled;
    
    private int port;

    public static void main(String[] args) {

        new AnnotationConfigApplicationContext(Application.class);
    }
    
    @PostConstruct
    public void init() {
        log.info("init() -- Initializing application");
        
        try {

            String httpPort =System.getProperty("HTTP_PORT", "54322");
            port =Integer.parseInt(httpPort);
            
            //Verticle port and router config
            verticle.setPort(port);
            Router router = Router.router(Vertx.vertx());
            verticle.setRouter(router);
            
            //Verticle ssl https config
            JksOptions keyStoreOptions = new JksOptions();
            keyStoreOptions.setPath(sslKeystorePath).setPassword(sslKeystorePassword);
            
            JksOptions trustStoreOptions = new JksOptions();
            trustStoreOptions.setPath(sslTruststorePath).setPassword(sslTruststorePassword);
            
            VertxOptions options = new VertxOptions();
            
            if(sslEnabled){
                options.setEventBusOptions(new EventBusOptions()
                    .setSsl(true)
                    .setKeyStoreOptions(keyStoreOptions)
                    .setTrustStoreOptions(trustStoreOptions)
                    .setClientAuth(ClientAuth.NONE)
                    //.setClientAuth(ClientAuth.REQUIRED)
                );
            }
            
            verticle.setSslOptions(keyStoreOptions, trustStoreOptions);
            
            //Launch verticle
            Vertx.vertx(options).deployVerticle(verticle);
        }catch(Exception e) {
            log.error("Exception", e);
        }
    }
}
