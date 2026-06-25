package com.example.adoptions;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import javax.sql.DataSource;
import java.lang.annotation.*;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class AdoptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }


    // ahead of time (AOT)
    @Bean
    JdbcPostgresDialect jdbcPostgresDialect() {
        return JdbcPostgresDialect.INSTANCE;
    }
}

@Configuration
@EnableResilientMethods
@ImportHttpServices(CatFactsClient.class)
class CatsConfiguration {
}

@Controller
@ResponseBody
class CatsController {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final CatFactsClient catFactsClient;

    CatsController(CatFactsClient catFactsClient) {
        this.catFactsClient = catFactsClient;
    }

    @ConcurrencyLimit(10)
    @Retryable(maxRetries = 5, includes = IllegalStateException.class)
    @GetMapping("/cats")
    CatFacts facts() {
        if (this.counter.getAndIncrement() < 5) {
            IO.println("boom!");
            throw new IllegalStateException("Boom!");
        }
        IO.println("no boom!");
        return catFactsClient.facts();
    }
}

interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}

/*
@Component
class CatFactsClient {

    private final RestClient http;

    CatFactsClient(RestClient.Builder http) {
        this.http = http.build();
    }

    CatFacts facts() {
        return this.http
                .get()
                .uri("https://www.catfacts.net/api")
                .retrieve()
                .body(CatFacts.class);
    }

}
*/

record CatFact(String fact) {
}

record CatFacts(Collection<CatFact> facts) {
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@interface NriComponent {

    /**
     * Alias for {@link Component#value}.
     */
    @AliasFor(annotation = Component.class) String value() default "";

}

class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry, Environment env) {
       /* for (var i = 0; i < 10; i++)
            registry.registerBean(MyRunner.class, s -> s
                    .supplier(supplierContext -> new MyRunner(supplierContext.bean(DataSource.class))));
        */
    }
}

@Configuration
@Import(MyBeanRegistrar.class)
class MyConfiguration {
}

//
//    @Bean
//    MyRunner runner() {
//        return new MyRunner();
//    }
//}

//@NriComponent
class MyRunner implements ApplicationRunner {

    private final DataSource dataSource;

    MyRunner(DataSource dataSource) {
        this.dataSource = dataSource;
        Assert.notNull(this.dataSource, "db must not be null!");
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IO.println("hi!");
    }
}

// implicit: component-scanning
// explicit: java config
@Controller
@ResponseBody
class AdoptionsController {

    private final DogRepository repository;

    AdoptionsController(DogRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/dogs/{dogId}/adoptions")
    void adopt(@PathVariable int dogId, @RequestParam String owner) {
        this.repository.findById(dogId).ifPresent(dog -> {
            var updated = new Dog(dog.id(), dog.name(), owner, dog.description());
            this.repository.save(updated);
        });
    }
}


@Controller
@ResponseBody
class DogsController {

    private final DogRepository repository;

    DogsController(DogRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/dogs", version = "2.0")
    Collection<Map<String, Object>> dogsv2() {
        return this.repository.findAll().stream().map(d -> Map.of("id", d.id(), "fullName", (Object) d.name())).toList();
    }

    @GetMapping(value = "/dogs", version = "1.0")
    Collection<Dog> dogsv1() {
        return this.repository.findAll();
    }
}


@Controller
@ResponseBody
class ClientController {

    private final RestClient http;

    ClientController(RestClient.Builder http, MessageClient messageClient) {
        this.http = http.build();
        this.messageClient = messageClient;
    }

    private final MessageClient messageClient;

    @GetMapping("/")
    Message client(
            @RegisteredOAuth2AuthorizedClient("spring") OAuth2AuthorizedClient principal) {
       /* var at = principal.getAccessToken().getTokenValue();
        return this.http
                .get()
                .uri("http://localhost:8081/message")
                .headers(a -> a.setBearerAuth(at))
                .retrieve()
                .body(Message.class);*/
        return messageClient.message();
    }
}

@ImportHttpServices(MessageClient.class)
@Configuration
class MessageConfiguration {

    @Bean
    OAuth2RestClientHttpServiceGroupConfigurer serviceGroupConfigurer(
            OAuth2AuthorizedClientManager auth2AuthorizedClientManager) {
        return OAuth2RestClientHttpServiceGroupConfigurer
                .from(auth2AuthorizedClientManager);
    }

}


@ClientRegistrationId("spring")
interface MessageClient {

    @GetExchange("http://localhost:8081/message")
    Message message();
}

record Message(String message) {
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {

    // select * from dog where owner = ?
    Collection<Dog> findByOwner(String owner);

    // select * from dog where name = ?
    Collection<Dog> findByName(String name);
}

// look mom, no Lombok!
record Dog(@Id int id, String name, String owner, String description) {
}