package com.sandflow.smpte.mxf.annotationprocessor;

import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("com.sandflow.smpte.mxf.annotationprocessor.MXFClassDefinition")
public class ClassFactoryProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
    if (annotations.isEmpty())
      return false;

    StringBuilder builder = new StringBuilder();
    builder.append("package com.sandflow.smpte.mxf;\n");
    builder.append("import com.sandflow.smpte.mxf.ClassFactory;\n");
    builder.append("import com.sandflow.smpte.util.AUID;\n");
    builder.append("\n");

    for (var element: env.getElementsAnnotatedWith(MXFClassDefinition.class)) {
      TypeElement classElement = (TypeElement) element; 
      builder.append("import %s;\n".formatted(classElement.getQualifiedName()));
    }

    builder.append("\n");

    builder.append("public class ClassFactoryPopulator {\n");
    builder.append("    static {\n");
    for (var element: env.getElementsAnnotatedWith(MXFClassDefinition.class)) {
      TypeElement classElement = (TypeElement) element;
      MXFClassDefinition annotation = classElement.getAnnotation(MXFClassDefinition.class);
      builder.append(
          "        ClassFactory.put(AUID.fromURN(\"%s\"), %s.class);\n".formatted(annotation.Identification(),
          classElement.getSimpleName()));
    }

    builder.append("    }\n\n");
    builder.append("}\n");

    try {
      JavaFileObject file = processingEnv.getFiler().createSourceFile("com.sandflow.smpte.mxf.ClassFactoryPopulator");
      try (Writer writer = file.openWriter()) {
        writer.write(builder.toString());
      }
    } catch (Exception e) {
      processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write Factory: " + e.getMessage());
    }

    return true;
  }
}
