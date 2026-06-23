# `jjsonc` (Java JSON Compiler)

> [!NOTE]
> This project is inspired by Daniel Lemire's blog post [Parsing JSON at compile time with C++26 static reflection](https://lemire.me/blog/2026/06/14/parsing-json-at-compile-time-with-c26-static-reflection/).

`jjsonc` is a lightweight, zero-dependency Java JSON compiler that parses JSON files at compile-time and translates them into statically initialized, type-safe Java Records. 

By compiling JSON into Java Record files at build-time, `jjsonc` completely eliminates:
* Runtime parsing overhead (no JSON parser is initialized or run at startup).
* Reflection overhead.
* Runtime dependencies (no JSON library is shipped with the production artifact).
* Runtime crashes caused by malformed JSON config files (any syntax error causes a build-time compilation failure).

---

## 1. How It Works

During Java compilation (`javac`), an annotation processor intercepts annotated classes or interfaces, reads the referenced JSON configuration, infers its types (recursive records and array list structures), and generates a corresponding Java Record class populated with the constant literals.

For example, given `config.json`:
```json
{
  "width": 1920,
  "fullscreen": true,
  "window": { "x": 100, "y": 200 }
}
```

And an annotated interface:
```java
package com.example;

import org.weirdloop.CompileJson;

@CompileJson(resource = "config.json")
public interface AppConfig {}
```

The compiler automatically generates `AppConfigValue.java` during compilation:
```java
package com.example;

public final class AppConfigValue {
    public record Window(int x, int y) {}
    
    public static final int width = 1920;
    public static final boolean fullscreen = true;
    public static final Window window = new Window(100, 200);

    private AppConfigValue() {}
}
```
You can access fields directly at runtime via `AppConfigValue.width` with **zero** parsing cost.

### Annotation Configuration

The `@CompileJson` annotation supports the following parameters:

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `resource` | `String` | **Yes** | The file path to the JSON configuration relative to resource folders (e.g., `config.json`). |
| `className` | `String` | No | The custom name of the generated Java class. Defaults to `[AnnotatedTypeName]Value` if not supplied. |

#### Package Inheritance
The generated Java class **inherits the exact same package** as the annotated interface or class. For example, if you annotate `com.example.AppConfig`, the generated record will be placed in the `com.example` package (as `com.example.AppConfigValue` or `com.example.AppConfigCompiled`), allowing package-private or public access matching your needs.

---

## 2. Dev Steps to Build and Publish

### Prerequisites
* Java 17 or higher
* Gradle (version 8+)

### Building and Publishing Locally
To compile the library and publish it to your local Maven repository (`~/.m2/repository`), run the following Gradle task from the `jjsonc` root directory:

```bash
gradle publishToMavenLocal
```

This generates and registers the artifact:
`org.weirdloop:jjsonc:1.0.0`

---

## 3. How to Use in Your Project

Once published to `mavenLocal()`, you can integrate `jjsonc` into your build tools.

### Gradle Integration
Add the following to your `build.gradle` file:

```groovy
repositories {
    mavenLocal()     // Required to resolve the local jjsonc artifact
    mavenCentral()
}

dependencies {
    // Only compile-time dependency for the @CompileJson annotation
    compileOnly 'org.weirdloop:jjsonc:1.0.0'
    
    // Trigger the compiler during compilation
    annotationProcessor 'org.weirdloop:jjsonc:1.0.0'
}
```

### Maven Integration
Add the following to your `pom.xml` file:

```xml
<dependencies>
    <!-- Compile-only dependency for the annotation -->
    <dependency>
        <groupId>org.weirdloop</groupId>
        <artifactId>jjsonc</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <!-- Trigger the compiler during compilation -->
                    <path>
                        <groupId>org.weirdloop</groupId>
                        <artifactId>jjsonc</artifactId>
                        <version>1.0.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Resource File Setup
Place your JSON configuration files in your module's standard resource folder:
`src/main/resources/config.json`

During execution, `jjsonc` resolves the resource path using a custom build-daemon traversal algorithm that correctly locates standard Maven/Gradle resource paths.

---

## 4. Standalone CLI Usage (Optional)

`jjsonc` can also be run as a standalone command line tool to compile JSON directly to Java source files:

```bash
java -cp jjsonc.jar org.weirdloop.JJsonC <input.json> <output_dir> <class_name> [package_name]
```
Example:
```bash
java -cp jjsonc.jar org.weirdloop.JJsonC config.json src/main/java AppConfig com.example
```
This generates `AppConfig.java` in `src/main/java/com/example/AppConfig.java`.
