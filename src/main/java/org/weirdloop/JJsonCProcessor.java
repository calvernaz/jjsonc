package org.weirdloop;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Set;

@SupportedAnnotationTypes("org.weirdloop.CompileJson")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class JJsonCProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(CompileJson.class)) {
            CompileJson compileJson = element.getAnnotation(CompileJson.class);
            if (compileJson == null) continue;

            String resourcePath = compileJson.resource();
            String className = compileJson.className();
            if (className.isEmpty()) {
                className = element.getSimpleName().toString() + "Value";
            }

            PackageElement pkg = processingEnv.getElementUtils().getPackageOf(element);
            String packageName = pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();

            try {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Compiling JSON resource: " + resourcePath + " into Java record " + className);

                String jsonContent = readResource(packageName, resourcePath);
                Object root = new JJsonC.JsonParser(jsonContent).parse();

                JJsonC compiler = new JJsonC();
                String javaSource = compiler.generateJavaSource(root, packageName, className);

                JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(
                    (packageName.isEmpty() ? "" : packageName + ".") + className, element);

                try (Writer writer = sourceFile.openWriter()) {
                    writer.write(javaSource);
                }

            } catch (Exception e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to compile JSON resource " + resourcePath + ": " + e.getMessage(), element);
                e.printStackTrace();
            }
        }
        return true;
    }

    private String readResource(String packageName, String resourcePath) throws IOException {
        // 1. Try StandardLocation.SOURCE_PATH relative to the package
        try {
            FileObject fileObject = processingEnv.getFiler().getResource(
                StandardLocation.SOURCE_PATH, packageName, resourcePath);
            return fileObject.getCharContent(true).toString();
        } catch (Exception e1) {
            // 2. Try StandardLocation.SOURCE_PATH relative to root
            try {
                FileObject fileObject = processingEnv.getFiler().getResource(
                    StandardLocation.SOURCE_PATH, "", resourcePath);
                return fileObject.getCharContent(true).toString();
            } catch (Exception e2) {
                // 3. Try finding the project root relative to CLASS_OUTPUT and look in src/main/resources
                File classOutputDir = getClassOutputDir();
                if (classOutputDir != null) {
                    File projectRoot = findProjectRoot(classOutputDir);
                    if (projectRoot != null) {
                        File resFile = new File(projectRoot, "src/main/resources/" + resourcePath);
                        if (resFile.exists()) {
                            return Files.readString(resFile.toPath());
                        }
                        File directFile = new File(projectRoot, resourcePath);
                        if (directFile.exists()) {
                            return Files.readString(directFile.toPath());
                        }
                    }
                }

                // 4. Try StandardLocation.CLASS_PATH relative to package
                try {
                    FileObject fileObject = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_PATH, packageName, resourcePath);
                    return fileObject.getCharContent(true).toString();
                } catch (Exception e3) {
                    // 5. Try StandardLocation.CLASS_PATH relative to root
                    try {
                        FileObject fileObject = processingEnv.getFiler().getResource(
                            StandardLocation.CLASS_PATH, "", resourcePath);
                        return fileObject.getCharContent(true).toString();
                    } catch (Exception e4) {
                        // 6. Try standard filesystem location relative to working directory
                        File file = new File(resourcePath);
                        if (file.exists()) {
                            return Files.readString(file.toPath());
                        }

                        // 7. Try under src/main/resources
                        File resFile = new File("src/main/resources", resourcePath);
                        if (resFile.exists()) {
                            return Files.readString(resFile.toPath());
                        }

                        // 8. Try parent modules or sibling dirs (e.g. if run from a submodule)
                        File siblingResFile = new File("../src/main/resources", resourcePath);
                        if (siblingResFile.exists()) {
                            return Files.readString(siblingResFile.toPath());
                        }

                        throw new FileNotFoundException("Could not locate compile-time JSON resource: '" + resourcePath + "'\n" +
                            "Tried SOURCE_PATH, CLASS_OUTPUT project root, CLASS_PATH, local relative files, and src/main/resources.");
                    }
                }
            }
        }
    }

    private File getClassOutputDir() {
        try {
            FileObject dummy = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "jjsonc_dummy");
            File file = new File(dummy.toUri());
            file.delete();
            return file.getParentFile();
        } catch (Exception e) {
            try {
                FileObject dummy = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", "");
                return new File(dummy.toUri());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private File findProjectRoot(File dir) {
        File current = dir;
        while (current != null) {
            if (new File(current, "build.gradle").exists() ||
                new File(current, "pom.xml").exists() ||
                new File(current, "src/main/resources").exists() ||
                new File(current, "settings.gradle").exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }
}
