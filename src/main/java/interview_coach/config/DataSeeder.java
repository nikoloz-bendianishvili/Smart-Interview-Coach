package interview_coach.config;

import interview_coach.entities.CodingChallenge;
import interview_coach.entities.Option;
import interview_coach.entities.Question;
import interview_coach.entities.TestCase;
import interview_coach.entities.Topic;
import interview_coach.entities.User;
import interview_coach.enums.Difficulty;
import interview_coach.enums.QuestionType;
import interview_coach.enums.Role;
import interview_coach.repositories.TopicRepository;
import interview_coach.services.core.QuestionService;
import interview_coach.services.core.TopicService;
import interview_coach.services.core.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Populates the database with demo topics, questions and users so a fresh
 * clone (or a fresh Docker volume) has something to practice against
 * immediately - without this, every session type throws
 * InsufficientQuestionsException on an empty database.
 * <p>
 * Enabled only via {@code app.seed.enabled=true} (SEED_ENABLED env var),
 * and idempotent: it no-ops once any topic already exists, so restarting
 * the app never duplicates data.
 * <p>
 * This is demo/seed data for a portfolio project, not a migration -
 * intentionally not tied to Flyway/Liquibase (see README "Design decisions").
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final TopicRepository topicRepository;
    private final TopicService topicService;
    private final QuestionService questionService;
    private final UserService userService;

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    public CommandLineRunner seedDatabase() {
        return args -> {
            if (topicRepository.count() > 0) {
                log.info("Seed data already present, skipping DataSeeder.");
                return;
            }

            log.info("Seeding demo data...");

            Topic java = topic("Java Fundamentals", "Core language features, OOP and the JVM.");
            Topic dsa = topic("Data Structures & Algorithms", "Complexity, common structures and classic problems.");
            Topic systemDesign = topic("System Design", "Scalability, caching, load balancing and distributed tradeoffs.");
            Topic springDb = topic("Spring & Databases", "Spring core concepts, transactions and relational databases.");

            seedUsers();
            seedMcqQuestions(java, dsa, systemDesign, springDb);
            seedCodingQuestions(java, dsa, springDb);
            seedOpenEndedQuestions(java, dsa, systemDesign, springDb);

            log.info("Seed data ready. Demo logins -> user: demo.user@example.com / Password123! " +
                    "| admin: demo.admin@example.com / Password123!");
        };
    }

    private Topic topic(String name, String description) {
        Topic t = Topic.builder().topicName(name).description(description).build();
        topicService.createTopic(t);
        return t;
    }

    private void seedUsers() {
        User user = User.builder()
                .firstName("Demo")
                .lastName("User")
                .webName("demo-user")
                .email("demo.user@example.com")
                .passwordHash("Password123!") // hashed by UserService.register
                .role(Role.USER)
                .targetRole("Backend Engineer")
                .isBanned(false)
                .isVerified(true)
                .build();
        userService.register(user);

        User admin = User.builder()
                .firstName("Demo")
                .lastName("Admin")
                .webName("demo-admin")
                .email("demo.admin@example.com")
                .passwordHash("Password123!") // hashed by UserService.register
                .role(Role.ADMIN)
                .targetRole("Backend Engineer")
                .isBanned(false)
                .isVerified(true)
                .build();
        userService.register(admin);
    }

    // --- MCQ ---------------------------------------------------------

    private void seedMcqQuestions(Topic java, Topic dsa, Topic systemDesign, Topic springDb) {
        mcq(java, Difficulty.EASY,
                "Which keyword prevents a class from being subclassed in Java?",
                1, "final", "static", "private", "abstract");
        mcq(java, Difficulty.EASY,
                "What is the default value of a boolean instance variable in Java?",
                2, "true", "false", "null", "0");
        mcq(java, Difficulty.MEDIUM,
                "Which of these Collections implementations preserves insertion order?",
                3, "HashSet", "TreeSet", "LinkedHashSet", "HashMap");

        mcq(dsa, Difficulty.MEDIUM,
                "What is the time complexity of binary search on a sorted array of size n?",
                2, "O(n)", "O(log n)", "O(n log n)", "O(1)");
        mcq(dsa, Difficulty.EASY,
                "Which data structure processes elements in LIFO order?",
                2, "Queue", "Stack", "Heap", "Linked List");
        mcq(dsa, Difficulty.HARD,
                "What is the worst-case time complexity of quicksort?",
                3, "O(n log n)", "O(n)", "O(n^2)", "O(log n)");

        mcq(systemDesign, Difficulty.MEDIUM,
                "Which of the following best describes a CDN?",
                1,
                "A caching layer distributed geographically close to users",
                "A type of database index",
                "A load balancing algorithm",
                "A message queue");
        mcq(systemDesign, Difficulty.HARD,
                "Which of these is NOT one of the three properties in the CAP theorem?",
                4, "Consistency", "Availability", "Partition tolerance", "Performance");
        mcq(systemDesign, Difficulty.MEDIUM,
                "Which caching strategy writes to the cache and the database at the same time?",
                1, "Write-through", "Write-back", "Write-around", "Cache-aside");

        mcq(springDb, Difficulty.EASY,
                "Which annotation marks a method to run within a database transaction in Spring?",
                1, "@Transactional", "@Async", "@Scheduled", "@Component");
        mcq(springDb, Difficulty.MEDIUM,
                "Which of these is NOT one of the four ACID properties?",
                4, "Atomicity", "Consistency", "Isolation", "Availability");
        mcq(springDb, Difficulty.EASY,
                "Which HTTP status code indicates a successful resource creation?",
                2, "200", "201", "204", "400");
    }

    private void mcq(Topic topic, Difficulty difficulty, String statement, int correctOption, String... options) {
        Question question = Question.builder()
                .topic(topic)
                .statement(statement)
                .questionType(QuestionType.MCQ)
                .difficulty(difficulty)
                .timeLimit(60)
                .score(10)
                .explanation("The correct answer is option " + correctOption + ": " + options[correctOption - 1] + ".")
                .build();

        Option option = Option.builder()
                .correctOption(correctOption)
                .option1(options[0])
                .option2(options[1])
                .option3(options.length > 2 ? options[2] : null)
                .option4(options.length > 3 ? options[3] : null)
                .build();

        questionService.createMCQQuestion(question, option);
    }

    // --- CODING --------------------------------------------------------

    private void seedCodingQuestions(Topic java, Topic dsa, Topic springDb) {
        coding(java, Difficulty.EASY,
                "Sum of Two Numbers",
                "Read two integers from standard input (space or newline separated) and print their sum.",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read two integers from stdin and print their sum
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                Scanner sc = new Scanner(System.in);
                                int a = sc.nextInt();
                                int b = sc.nextInt();
                                System.out.println(a + b);
                            }
                        }""",
                List.of(
                        tc("2 3", "5", false),
                        tc("10 -4", "6", false),
                        tc("0 0", "0", true)
                ));

        coding(java, Difficulty.EASY,
                "Palindrome Check",
                "Read a line of input and print \"true\" if it reads the same forwards and backwards, \"false\" otherwise.",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read a line and print true/false
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                Scanner sc = new Scanner(System.in);
                                String s = sc.nextLine();
                                System.out.println(new StringBuilder(s).reverse().toString().equals(s));
                            }
                        }""",
                List.of(
                        tc("level", "true", false),
                        tc("hello", "false", false),
                        tc("a", "true", true)
                ));

        coding(dsa, Difficulty.MEDIUM,
                "FizzBuzz",
                "Read an integer N and print the numbers 1..N, one per line: \"Fizz\" for multiples of 3, " +
                        "\"Buzz\" for multiples of 5, \"FizzBuzz\" for multiples of both, otherwise the number itself.",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read N and print the FizzBuzz sequence, one value per line
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                int n = new Scanner(System.in).nextInt();
                                StringBuilder sb = new StringBuilder();
                                for (int i = 1; i <= n; i++) {
                                    if (i % 15 == 0) sb.append("FizzBuzz");
                                    else if (i % 3 == 0) sb.append("Fizz");
                                    else if (i % 5 == 0) sb.append("Buzz");
                                    else sb.append(i);
                                    sb.append(System.lineSeparator());
                                }
                                System.out.print(sb);
                            }
                        }""",
                List.of(
                        tc("5", "1\n2\nFizz\n4\nBuzz", false),
                        tc("15", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz", true)
                ));

        coding(dsa, Difficulty.MEDIUM,
                "Maximum in Array",
                "Read an integer N, then N integers, and print the largest one.",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read N, then N integers, and print the maximum
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                Scanner sc = new Scanner(System.in);
                                int n = sc.nextInt();
                                int max = Integer.MIN_VALUE;
                                for (int i = 0; i < n; i++) {
                                    max = Math.max(max, sc.nextInt());
                                }
                                System.out.println(max);
                            }
                        }""",
                List.of(
                        tc("4\n3 7 2 9", "9", false),
                        tc("3\n-5 -1 -10", "-1", true)
                ));

        coding(dsa, Difficulty.HARD,
                "Nth Fibonacci Number",
                "Read an integer N (0-indexed) and print the Nth Fibonacci number (fib(0) = 0, fib(1) = 1).",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read N and print fib(N)
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                int n = new Scanner(System.in).nextInt();
                                long a = 0, b = 1;
                                for (int i = 0; i < n; i++) {
                                    long next = a + b;
                                    a = b;
                                    b = next;
                                }
                                System.out.println(a);
                            }
                        }""",
                List.of(
                        tc("0", "0", false),
                        tc("10", "55", false),
                        tc("1", "1", true)
                ));

        coding(springDb, Difficulty.MEDIUM,
                "Reverse a String",
                "Read a line of input and print it reversed.",
                """
                        public class Main {
                            public static void main(String[] args) {
                                // Read a line and print it reversed
                            }
                        }""",
                """
                        import java.util.Scanner;

                        public class Main {
                            public static void main(String[] args) {
                                String s = new Scanner(System.in).nextLine();
                                System.out.println(new StringBuilder(s).reverse());
                            }
                        }""",
                List.of(
                        tc("hello", "olleh", false),
                        tc("Spring", "gnirpS", true)
                ));
    }

    private TestCase tc(String input, String expectedOutput, boolean hidden) {
        return TestCase.builder().input(input).expectedOutput(expectedOutput).isHidden(hidden).build();
    }

    private void coding(Topic topic, Difficulty difficulty, String title, String statement,
                         String starterCode, String referenceSolution, List<TestCase> testCases) {
        Question question = Question.builder()
                .topic(topic)
                .statement(title + ": " + statement)
                .questionType(QuestionType.CODING)
                .difficulty(difficulty)
                .timeLimit(600)
                .score(20)
                .explanation("Reference approach: " + referenceSolution)
                .build();

        CodingChallenge challenge = CodingChallenge.builder()
                .starterCode(starterCode)
                .referenceSolution(referenceSolution)
                .build();

        questionService.createCodingQuestion(question, challenge, testCases);
    }

    // --- OPEN_ENDED ------------------------------------------------------

    private void seedOpenEndedQuestions(Topic java, Topic dsa, Topic systemDesign, Topic springDb) {
        openEnded(java, Difficulty.MEDIUM,
                "Explain the difference between == and .equals() in Java, and why overriding equals() " +
                        "without hashCode() is dangerous.",
                "== compares references (identity) for objects and values for primitives, while .equals() " +
                        "compares logical equality and can be overridden. If equals() is overridden without also " +
                        "overriding hashCode(), two logically-equal objects can produce different hash codes, " +
                        "breaking the contract used by HashMap/HashSet/HashTable - equal objects may end up in " +
                        "different buckets and lookups silently fail.");
        openEnded(java, Difficulty.EASY,
                "What is the difference between checked and unchecked exceptions in Java, and when would you " +
                        "use each?",
                "Checked exceptions (subclasses of Exception, not RuntimeException) must be declared or caught " +
                        "at compile time and represent recoverable conditions the caller should handle, such as " +
                        "IOException. Unchecked exceptions (subclasses of RuntimeException) aren't enforced by " +
                        "the compiler and typically represent programming errors, such as NullPointerException " +
                        "or IllegalArgumentException.");
        openEnded(java, Difficulty.HARD,
                "Explain Java's garbage collection at a high level. What makes an object eligible for " +
                        "collection?",
                "The JVM's garbage collector automatically reclaims heap memory used by objects that are no " +
                        "longer reachable from any GC root (local variables on the stack, static fields, active " +
                        "threads, etc). Most collectors use a generational approach: short-lived objects are " +
                        "allocated in a young generation and collected frequently and cheaply, while long-lived " +
                        "objects are promoted to an old generation collected less often.");

        openEnded(dsa, Difficulty.EASY,
                "Explain the difference between a stack and a queue, and give a real-world use case for each.",
                "A stack is LIFO (last in, first out) - the most recently added element is removed first; " +
                        "used for undo history or call-stack tracking. A queue is FIFO (first in, first out) - " +
                        "elements are removed in the order they were added; used for task scheduling or " +
                        "request buffering.");
        openEnded(dsa, Difficulty.MEDIUM,
                "What is Big-O notation and why does it matter when choosing between two algorithms that " +
                        "solve the same problem?",
                "Big-O describes how an algorithm's running time or space usage grows relative to input size " +
                        "in the worst case, ignoring constant factors. It matters because two algorithms can " +
                        "produce identical results but scale very differently - an O(n^2) algorithm may be " +
                        "faster than an O(n log n) one on small inputs but far slower as input grows.");
        openEnded(dsa, Difficulty.HARD,
                "Describe how a hash map achieves average O(1) lookup, and explain what happens on a hash " +
                        "collision.",
                "A hash map applies a hash function to the key to compute a bucket index, so lookups can jump " +
                        "directly to the right bucket instead of scanning all entries, giving average O(1) access. " +
                        "On a collision (two keys hashing to the same bucket), most implementations either chain " +
                        "colliding entries in a linked list/tree at that bucket, or probe for another open slot " +
                        "(open addressing).");

        openEnded(systemDesign, Difficulty.MEDIUM,
                "What is the difference between horizontal and vertical scaling? What are the tradeoffs of " +
                        "each?",
                "Vertical scaling adds more resources (CPU/RAM) to a single machine - simple, but has a hard " +
                        "ceiling and a single point of failure. Horizontal scaling adds more machines and " +
                        "distributes load across them - it scales further and improves fault tolerance, but " +
                        "introduces complexity around load balancing, data consistency and coordination.");
        openEnded(systemDesign, Difficulty.MEDIUM,
                "Explain what a load balancer does and describe at least two load-balancing strategies.",
                "A load balancer distributes incoming requests across multiple backend instances so no single " +
                        "server is overwhelmed, improving availability and throughput. Common strategies include " +
                        "round robin (requests cycle evenly across servers), least connections (route to the " +
                        "server currently handling the fewest active requests), and IP hashing (route based on a " +
                        "hash of the client's IP for session affinity).");
        openEnded(systemDesign, Difficulty.HARD,
                "What is database sharding, and what problem does it solve?",
                "Sharding splits a single logical database into multiple smaller databases (shards), each " +
                        "holding a subset of the data, typically partitioned by a key like user ID. It solves the " +
                        "problem of a single database server running out of capacity (storage, throughput, or " +
                        "connections) by spreading data and load across multiple machines, at the cost of added " +
                        "complexity for cross-shard queries and transactions.");

        openEnded(springDb, Difficulty.EASY,
                "Explain the difference between @Component, @Service, and @Repository in Spring. Are they " +
                        "functionally different?",
                "All three are stereotype annotations that register a class as a Spring bean, and @Service and " +
                        "@Repository are themselves meta-annotated with @Component, so functionally they behave " +
                        "the same for bean discovery. The difference is semantic/documentation: @Service marks " +
                        "business-logic classes, @Repository marks data-access classes (and additionally enables " +
                        "Spring's exception translation for persistence exceptions).");
        openEnded(springDb, Difficulty.HARD,
                "What is the N+1 query problem in an ORM like Hibernate, and how would you fix it?",
                "It happens when fetching a list of N parent entities lazily triggers one additional query per " +
                        "parent to fetch an associated collection or reference, resulting in 1 + N queries instead " +
                        "of one. It's typically fixed with a JOIN FETCH in the query, an entity graph, or by " +
                        "batching the association fetch, so related data is retrieved in a single round trip.");
        openEnded(springDb, Difficulty.MEDIUM,
                "Explain the difference between optimistic and pessimistic locking in a database.",
                "Pessimistic locking acquires a lock on a row before reading/modifying it, blocking other " +
                        "transactions until it's released - safe under high contention but reduces concurrency. " +
                        "Optimistic locking assumes conflicts are rare: it reads without locking and checks a " +
                        "version/timestamp column at write time, failing the write if another transaction changed " +
                        "the row in between - higher throughput, but callers must handle the conflict.");
    }

    private void openEnded(Topic topic, Difficulty difficulty, String statement, String modelAnswer) {
        Question question = Question.builder()
                .topic(topic)
                .statement(statement)
                .questionType(QuestionType.OPEN_ENDED)
                .difficulty(difficulty)
                .timeLimit(180)
                .score(15)
                .explanation(modelAnswer)
                .build();

        questionService.createOpenEndedQuestion(question);
    }
}
