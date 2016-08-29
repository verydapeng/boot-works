package singasug;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@SpringBootApplication
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}

@RestController
class SimpleController {

    @RequestMapping("/")
    String hello() {
        return "hello singapore spring user group ";
    }
}


// simple jpa entity mapping
@Entity
class Book {

    @Id
    @GeneratedValue
    long id;

    @Size(min = 1)
    private String title;
    @Min(1900)
    private int year;

    protected Book() {} // mandates by JPA

    public Book(String title, int year) {
        this.title = title;
        this.year = year;
    }

    public long getId() { return id; }

    public String getTitle() { return title; }

    public int getYear() { return year; }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + '\'' + ", year=" + year + '}';
    }
}

// spring data implements this interface for us
@Repository
@RepositoryRestResource
interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByTitle(@Param("title") String title);

    List<Book> findByYearLessThan(@Param("year") int year);
}

@RestController
class BookController {

    @Autowired
    BookRepository repository;

    @RequestMapping("/b/{id}")
    Book getById(@PathVariable long id) {
        return repository.findOne(id);
    }
}

@Configuration
class BookConfig {

    @Bean
    ApplicationRunner init(BookRepository repository) {
        // init db
        return args -> {
            repository.save(new Book("Java tutorial", 1995));
            repository.save(new Book("Spring reference", 2016));
        };
    }
}

@RestController
class ValidationBookController {

    @RequestMapping("/b/validationOff")
    String validationOff(@RequestBody Book book) {
        System.out.println(book);
        return "OK";
    }

    @RequestMapping("/b/validationOn")
    String validationOn(@Valid @RequestBody Book book) {
        System.out.println(book);
        return "OK";
    }

}


@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "username"))
class Account {

    @Id
    @GeneratedValue
    private long id;
    private String username, password, authorities;

    protected Account() {}

    public Account(String username, String password, String authorities) {
        this.username = username;
        this.password = password;
        this.authorities = authorities;
    }

    public long getId() { return id; }

    public String getUsername() { return username; }

    public String getPassword() { return password; }

    public String getAuthorities() { return authorities; }
}

@Repository
interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);
}

// actual configuration
@Configuration
class SecurityConfig {

    @Bean
    WebSecurityConfigurerAdapter webSecurityConfigurerAdapter() {
        return new WebSecurityConfigurerAdapter() {
            @Override
            protected void configure(HttpSecurity http) throws Exception {
                http
                        .csrf().disable() // enables HTTP GET for /logout, not recommended in prod

                        .authorizeRequests()
                        .antMatchers("/b/**").hasAnyAuthority("USER")
                        .antMatchers("/books/**").hasRole("ADMIN")
                        .anyRequest().authenticated()


                        .and().formLogin()
                        .and().logout().permitAll();
            }

        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accountRepository) {
        return username -> accountRepository.findByUsername(username)
                .map(a -> new User(
                        a.getUsername(),
                        a.getPassword(),
                        AuthorityUtils.commaSeparatedStringToAuthorityList(a.getAuthorities())))
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    ApplicationRunner initUsers(PasswordEncoder encoder, AccountRepository repository) {
        // init db, for demo
        return args -> Stream.of(
                new Account("user", encoder.encode("password"), "USER"),
                new Account("admin", encoder.encode("password"), "USER,ROLE_ADMIN")).forEach(repository::save);
    }

}

@RestController
class FlipFlopController implements HealthIndicator {
    private final AtomicLong requestCount = new AtomicLong();
    private final CounterService counterService;

    FlipFlopController(CounterService counterService) {
        this.counterService = counterService;
    }

    @Override
    public Health health() {
        long count = requestCount.get();
        return count % 2 == 1
                ? Health.up().build()
                : Health.down().withDetail("count", count).build();
    }

    @RequestMapping("/metered")
    String metered() {
        counterService.increment("metered.request.count");
        return "requests served: " + requestCount.incrementAndGet();
    }
}
