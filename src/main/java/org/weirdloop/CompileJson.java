package org.weirdloop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers the compile-time JSON compiler (jjsonc) to compile the specified JSON
 * resource into a statically populated Java Record.
 *
 * <p><strong>Package Inheritance:</strong>
 * The generated Java class will belong to the exact same package namespace as the
 * annotated class or interface.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CompileJson {
    /**
     * The path to the JSON file to parse at compile-time.
     * This path should be relative to the source directories, resource folders
     * (e.g., "src/main/resources/config.json"), or standard resource locations.
     *
     * @return the relative path of the JSON resource
     */
    String resource();

    /**
     * Optional: The custom name of the generated Java class.
     *
     * <p><strong>Default Behavior:</strong>
     * If not provided (or left empty), defaults to the annotated type name
     * with the word "Value" appended (e.g., annotating class {@code AppConfig}
     * generates {@code AppConfigValue}).
     *
     * @return the custom class name, or empty string to use the default
     */
    String className() default "";
}
