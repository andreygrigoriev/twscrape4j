# twscrape4j — Developer Notes

## Jackson 3 conventions

This project uses **Jackson 3.x** (`tools.jackson.*`). All Jackson imports must use the new package
prefix — the old `com.fasterxml.jackson.*` prefix will fail to compile.

| Jackson 2 (old)                                  | Jackson 3 (required)                        |
|--------------------------------------------------|---------------------------------------------|
| `com.fasterxml.jackson.databind.JsonNode`        | `tools.jackson.databind.JsonNode`           |
| `com.fasterxml.jackson.databind.ObjectMapper`    | `tools.jackson.databind.ObjectMapper`       |
| `com.fasterxml.jackson.databind.json.JsonMapper` | `tools.jackson.databind.json.JsonMapper`    |

### Mapper construction

Prefer `JsonMapper.builder().build()` over `new ObjectMapper()` when constructing mappers in tests
and production code. `JavaTimeModule` is no longer needed — Java 8 time types (`Instant`, etc.) are
supported natively in Jackson 3 core.

```java
// Preferred:
private static final ObjectMapper MAPPER = JsonMapper.builder().build();

// Avoid — still works but lacks explicit builder configuration:
private static final ObjectMapper MAPPER = new ObjectMapper();
```

### Maven dependency

```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>${jackson.version}</version>
</dependency>
```

Jackson 3 version is pinned in `pom.xml` via `<jackson.version>`. See the comment there for GA
status verification instructions.
