package com.sandflow.smpte.mxf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import com.sandflow.smpte.regxml.dict.definitions.ClassDefinition;
import com.sandflow.smpte.regxml.dict.definitions.Definition;
import com.sandflow.smpte.regxml.dict.definitions.PropertyDefinition;
import com.sandflow.smpte.util.AUID;

public class ClassGenerator {
  public static final Handlebars handlebars = new Handlebars();
  public static final Template classTemplate;

  static {
    try {
      classTemplate = handlebars.compile("hbs/MXFClass.java");
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template", e);
    }
  }

  public static void generate(MetaDictionaryCollection mds, File generatedSourcesDir)
      throws IOException, URISyntaxException {

    HashMap<AUID, String> typeNames = new HashMap<>();

    for (var md : mds.getDictionaries()) {

      if (md.getSchemeURI().toString().equals("http://www.ebu.ch/metadata/schemas/ebucore/smpte/class13/group"))
        continue;

      for (var def : md.getDefinitions()) {

        if (!(def instanceof ClassDefinition))
          continue;

        ClassDefinition classDef = (ClassDefinition) def;

        var data = new HashMap<String, Object>();

        data.put("className", classDef.getSymbol());
        data.put("identification", classDef.getIdentification().toString());
        if (!classDef.isConcrete()) {
          data.put("isAbstract", "1");
        }

        AUID parentClassID = classDef.getParentClass();
        if (parentClassID != null) {
          var parentClass = (ClassDefinition) mds.getDefinition(classDef.getParentClass());

          data.put("parentClassName", parentClass.getSymbol());
        }

        var members = new ArrayList<HashMap<String, String>>();

        for (var propertyAUID : mds.getMembersOf(classDef)) {
          PropertyDefinition propertyDef = (PropertyDefinition) mds.getDefinition(propertyAUID);
          if (propertyDef == null) {
            throw new RuntimeException("Failed to find property definition for " + propertyAUID);
          }

          AUID typeAUID = propertyDef.getType();

          String typeName = typeNames.get(typeAUID);

          if (typeName == null) {

            Definition typeDef = mds.getDefinition(typeAUID);
            if (typeDef == null) {
              throw new RuntimeException("Failed to find type definition for " + propertyDef.getType());
            }

            typeName = "int";

          }

          var member = new HashMap<String, String>();
          member.put("identification", propertyDef.getIdentification().toString());
          member.put("type", propertyDef.getType().toString());
          member.put("typeName", typeName);
          member.put("symbol", propertyDef.getSymbol());
          member.put("localIdentification", Integer.toString(propertyDef.getLocalIdentification()));
          member.put("isOptional", propertyDef.isOptional() ? "true" : "false");

          members.add(member);
        }

        data.put("members", members);

        try {
          var classFile = new File(generatedSourcesDir, def.getSymbol() + ".java");
          var os = new FileWriter(classFile);
          os.write(classTemplate.apply(data));
          os.close();
        } catch (Exception e) {
          throw new RuntimeException("Failed to write class file", e);
        }

      }
    }
  }
}
