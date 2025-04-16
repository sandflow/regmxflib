package com.sandflow.smpte.mxf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import com.sandflow.smpte.regxml.dict.definitions.ClassDefinition;
import com.sandflow.smpte.util.AUID;

public class ClassGenerator {

  public static void generate(MetaDictionaryCollection mds, File generatedSourcesDir) throws IOException, URISyntaxException {
    Handlebars handlebars = new Handlebars();
    Template template = handlebars.compile("hbs/MXFClass.java");

    for(var md: mds.getDictionaries()) {

      for(var def: md.getDefinitions()) {

        if (!(def instanceof ClassDefinition))
          continue;

        ClassDefinition classDef = (ClassDefinition) def;

        var data = new HashMap<String, String>();

        data.put("className", classDef.getSymbol());
        
        AUID parentClassID = classDef.getParentClass();
        if (parentClassID != null) {
          var parentClass = (ClassDefinition) mds.getDefinition(classDef.getParentClass());

          data.put("parentClassName", parentClass.getSymbol());
        }

        var classFile = new File(generatedSourcesDir, classDef.getSymbol() + ".java");

        var os = new FileWriter(classFile);

        os.write(template.apply(data));

        os.close();
      }
    }
  }
}
