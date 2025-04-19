package com.sandflow.smpte.mxf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpClient.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.commons.numbers.fraction.Fraction;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.sandflow.smpte.mxf.adapters.BooleanAdapter;
import com.sandflow.smpte.mxf.adapters.VersionAdapter;
import com.sandflow.smpte.regxml.dict.DefinitionResolver;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import com.sandflow.smpte.regxml.dict.definitions.CharacterTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.ClassDefinition;
import com.sandflow.smpte.regxml.dict.definitions.Definition;
import com.sandflow.smpte.regxml.dict.definitions.DefinitionVisitor.VisitorException;
import com.sandflow.smpte.regxml.dict.definitions.EnumerationTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.ExtendibleEnumerationTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.FixedArrayTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.FloatTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.IndirectTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.IntegerTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.LensSerialFloatTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.NullDefinitionVisitor;
import com.sandflow.smpte.regxml.dict.definitions.OpaqueTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.PropertyDefinition;
import com.sandflow.smpte.regxml.dict.definitions.RecordTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.RenameTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.SetTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.StreamTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.StringTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.StrongReferenceTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.VariableArrayTypeDefinition;
import com.sandflow.smpte.regxml.dict.definitions.WeakReferenceTypeDefinition;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;

public class ClassGenerator {
  public static final Handlebars handlebars = new Handlebars();
  public static final Template classTemplate;
  public static final Template enumerationTemplate;
  public static final Template variableArrayTemplate;
  public static final Template recordTemplate;

  static {
    try {
      classTemplate = handlebars.compile("hbs/Class.java");
      enumerationTemplate = handlebars.compile("hbs/Enumeration.java");
      variableArrayTemplate = handlebars.compile("hbs/VariableArrayAdapter.java");
      recordTemplate = handlebars.compile("hbs/Record.java");
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template", e);
    }
  }

  class TypeMaker extends NullDefinitionVisitor {
    private static final UL INSTANCE_UID_ITEM_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010101.01011502.00000000");
    private static final UL AUID_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.01.00.00.00.00.00");
    private static final UL UUID_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.03.00.00.00.00.00");
    private static final UL DateStruct_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.05.00.00.00.00.00");
    private static final UL PackageID_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.02.00.00.00.00.00");
    private static final UL Rational_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.01.00.00.00.00.00");
    private static final UL TimeStruct_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.06.00.00.00.00.00");
    private static final UL TimeStamp_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.07.00.00.00.00.00");
    private static final UL VersionType_UL = UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.03.00.00.00.00.00");
    private static final UL ByteOrder_UL = UL.fromDotValue("06.0E.2B.34.01.01.01.01.03.01.02.01.02.00.00.00");
    private static final UL Character_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100100.00000000");
    private static final UL Char_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100300.00000000");
    private static final UL UTF8Character_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100500.00000000");
    private static final UL ProductReleaseType_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.02010101.00000000");
    private static final UL Boolean_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01040100.00000000");
    private static final UL PrimaryPackage_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010104.06010104.01080000");
    private static final UL LinkedGenerationID_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010102.05200701.08000000");
    private static final UL GenerationID_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010102.05200701.01000000");
    private static final UL ApplicationProductID_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010102.05200701.07000000");
    private static final AUID AUID_AUID = new AUID(UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.01.00.00.00.00.00"));
    private static final AUID UUID_AUID = new AUID(UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.03.00.00.00.00.00"));
    private static final AUID DateStruct_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.05.00.00.00.00.00"));
    private static final AUID PackageID_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.01.03.02.00.00.00.00.00"));
    private static final AUID Rational_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.01.00.00.00.00.00"));
    private static final AUID TimeStruct_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.06.00.00.00.00.00"));
    private static final AUID TimeStamp_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.07.00.00.00.00.00"));
    private static final AUID VersionType_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.04.01.01.03.01.03.00.00.00.00.00"));
    private static final AUID ObjectClass_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.01.01.02.06.01.01.04.01.01.00.00"));
    private static final AUID ByteOrder_AUID = new AUID(
        UL.fromDotValue("06.0E.2B.34.01.01.01.01.03.01.02.01.02.00.00.00"));
    private static final AUID InstanceID_AUID = new AUID(
        UL.fromURN("urn:smpte:ul:060e2b34.01010101.01011502.00000000"));

    boolean isNullable;

    String typeName;
    String adapterName;

    public String getTypeName() {
      return typeName;
    }

    public String getAdapterName() {
      return adapterName;
    }

    private TypeMaker(boolean isNullabe) {
      this.isNullable = isNullabe;
    }

    @Override
    public void visit(ClassDefinition def) throws VisitorException {
      this.typeName = def.getSymbol();
      this.adapterName = "ClassAdapter";
    }

    @Override
    public void visit(CharacterTypeDefinition def) throws VisitorException {
      this.typeName = "String";

      if (def.getIdentification().equals(Character_UL)) {
        this.adapterName = "UTF16Adapter";
      } else if (def.getIdentification().equals(Char_UL)) {
        this.adapterName = "USASCIIAdapter";
      } else if (def.getIdentification().equals(UTF8Character_UL)) {
        this.adapterName = "UTF8Adapter";
      } else {
        throw new VisitorException("Unknown character type " + def.getIdentification());
      }
    }

    @Override
    public void visit(IntegerTypeDefinition def) throws VisitorException {
      if (def.isSigned()) {
        switch (def.getSize()) {
          case ONE:
            this.typeName = this.isNullable ? "Byte" : "byte";
            this.adapterName = "Int8Adapter";
            break;
          case TWO:
            this.typeName = this.isNullable ? "Short" : "short";
            this.adapterName = "Int16Adapter";
            break;
          case FOUR:
            this.typeName = this.isNullable ? "Integer" : "int";
            this.adapterName = "Int32Adapter";
            break;
          case EIGHT:
            this.typeName = this.isNullable ? "Long" : "long";
            this.adapterName = "Int64Adapter";
            break;
          default:
            throw new VisitorException("Unknown integer type " + def.getIdentification());
        }
      } else {
        switch (def.getSize()) {
          case ONE:
            this.typeName = this.isNullable ? "Short" : "short";
            this.adapterName = "UInt8Adapter";
            break;
          case TWO:
            this.typeName = this.isNullable ? "Integer" : "int";
            this.adapterName = "UInt16Adapter";
            break;
          case FOUR:
            this.typeName = this.isNullable ? "Long" : "long";
            this.adapterName = "UInt32Adapter";
            break;
          case EIGHT:
            this.typeName = this.isNullable ? "Long" : "long";
            this.adapterName = "UInt64Adapter";
            break;
          default:
            throw new VisitorException("Unknown integer type " + def.getIdentification());
        }
      }
    }

    @Override
    public void visit(ExtendibleEnumerationTypeDefinition def) throws VisitorException {
      this.typeName = "com.sandflow.smpte.util.UL";
      this.adapterName = "ULAdapter";
    }

    private final static UL BOOLEAN_TYPE = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01040100.00000000");

    @Override
    public void visit(EnumerationTypeDefinition def) throws VisitorException {
      if (BOOLEAN_TYPE.equals(def.getIdentification())) {
        this.adapterName = BooleanAdapter.class.getName();
        this.typeName = this.isNullable ? "Boolean" : "boolean";
        return;
      }

      var templateData = new HashMap<String, Object>();
      templateData.put("symbol", def.getSymbol());
      templateData.put("valuesTypeName", "int");

      var valuesData = new ArrayList<HashMap<String, String>>();
      templateData.put("values", valuesData);

      for (var value : def.getElements()) {
        var valueData = new HashMap<String, String>();
        valueData.put("name", value.getName());
        valueData.put("value", Integer.toString(value.getValue()));
        valuesData.add(valueData);
      }

      generateSource(enumerationTemplate, def.getSymbol(), templateData);

      this.adapterName = "EnumerationAdapter";
      this.typeName = def.getSymbol();
    }

    @Override
    public void visit(FixedArrayTypeDefinition def) throws VisitorException {
      var templateData = new HashMap<String, Object>();

      String adapterName = def.getSymbol() + "Adapter";
      templateData.put("adapterName", adapterName);
      templateData.put("itemCount", def.getElementCount());

      TypeMaker tm = getTypeInformation(resolver.getDefinition(def.getElementType()), false);
      templateData.put("itemTypeName", tm.getTypeName());

      generateSource(variableArrayTemplate, adapterName, templateData);

      this.adapterName = adapterName;
      this.typeName = tm.getTypeName() + "[]";
    }

    @Override
    public void visit(IndirectTypeDefinition def) throws VisitorException {
      // TODO Auto-generated method stub
    }

    @Override
    public void visit(OpaqueTypeDefinition def) throws VisitorException {
      // TODO Auto-generated method stub
    }

    @Override
    public void visit(RecordTypeDefinition def) throws VisitorException {
      if (def.getIdentification().equals(AUID_AUID)) {

        this.adapterName = "AUIDAdapter";
        this.typeName = AUID.class.getName();

      } else if (def.getIdentification().equals(DateStruct_AUID)) {

        this.adapterName = "LocalDateAdapter";
        this.typeName = LocalDate.class.getName();

      } else if (def.getIdentification().equals(PackageID_AUID)) {
        this.adapterName = "UMIDAdapter";
        this.typeName = UMID.class.getName();

      } else if (def.getIdentification().equals(Rational_AUID)) {

        this.adapterName = "RationalAdapter";
        this.typeName = Fraction.class.getName();

      } else if (def.getIdentification().equals(TimeStruct_AUID)) {

        this.adapterName = "LocalTimeAdapter";
        this.typeName = LocalTime.class.getName();

      } else if (def.getIdentification().equals(TimeStamp_AUID)) {

        this.adapterName = "LocalDateTimeAdapter";
        this.typeName = LocalDateTime.class.getName();

      } else if (def.getIdentification().equals(VersionType_AUID)) {

        this.adapterName = VersionAdapter.class.getName();
        this.typeName = Version.class.getName();

      } else {

        var templateData = new HashMap<String, Object>();
        templateData.put("name", def.getSymbol());

        var membersData = new ArrayList<HashMap<String, String>>();
        templateData.put("members", membersData);

        for (var member : def.getMembers()) {
          Definition memberTypeDef = resolver.getDefinition(member.getType());
          if (memberTypeDef == null) {
            throw new RuntimeException(
                String.format("Bad type %s at member %s.", member.getType().toString(), member.getName()));
          }
          TypeMaker tm = getTypeInformation(memberTypeDef, false);
          var valueData = new HashMap<String, String>();
          valueData.put("memberAdapterName", tm.getAdapterName());
          valueData.put("memberName", member.getName());
          valueData.put("memberTypeName", tm.getTypeName());
          membersData.add(valueData);
        }

        generateSource(recordTemplate, def.getSymbol(), templateData);
        this.adapterName = "RecordAdapter";

      }
    }

    @Override
    public void visit(RenameTypeDefinition def) throws VisitorException {
      TypeMaker tm = getTypeInformation(resolver.getDefinition(def.getRenamedType()), this.isNullable);

      this.typeName = tm.typeName;
      this.adapterName = tm.adapterName;
    }

    @Override
    public void visit(SetTypeDefinition def) throws VisitorException {
      this.typeName = isNullable ? "Integer" : "int";
      this.adapterName = "Int32Adapter";
    }

    @Override
    public void visit(StreamTypeDefinition def) throws VisitorException {
      this.typeName = isNullable ? "Integer" : "int";
      this.adapterName = "Int32Adapter";
    }

    @Override
    public void visit(StrongReferenceTypeDefinition def) throws VisitorException {
      ClassDefinition cdef = (ClassDefinition) resolver.getDefinition(def.getReferencedType());

      this.typeName = cdef.getSymbol();
      this.adapterName = "ClassAdapter";
    }

    @Override
    public void visit(StringTypeDefinition def) throws VisitorException {
      this.typeName = isNullable ? "Integer" : "int";
      this.adapterName = "Int32Adapter";
    }

    @Override
    public void visit(VariableArrayTypeDefinition def) throws VisitorException {
      var templateData = new HashMap<String, Object>();

      String adapterName = def.getSymbol() + "Adapter";
      templateData.put("adapterName", adapterName);

      Definition itemDef = resolver.getDefinition(def.getElementType());
      TypeMaker tm = getTypeInformation(itemDef, false);
      templateData.put("itemTypeName", tm.getTypeName());
      templateData.put("itemAdapterName", tm.getAdapterName());

      generateSource(variableArrayTemplate, adapterName, templateData);

      this.adapterName = adapterName;
      this.typeName = tm.getTypeName() + "[]";
    }

    @Override
    public void visit(WeakReferenceTypeDefinition def) throws VisitorException {
      ClassDefinition classdef = (ClassDefinition) resolver.getDefinition(def.getReferencedType());

      PropertyDefinition uniquepropdef = null;

      for (PropertyDefinition propdef : getAllMembersOf(classdef)) {

        if (propdef.isUniqueIdentifier()) {
          uniquepropdef = propdef;
          break;
        }
      }

      if (uniquepropdef == null) {
        throw new VisitorException("WeakReferenceTypeDefinition " + def.getIdentification()
            + " does not have a unique identifier");
      }

      TypeMaker tm = getTypeInformation(resolver.getDefinition(uniquepropdef.getType()), true);

      this.typeName = tm.typeName;
      this.adapterName = tm.adapterName;
    }

    @Override
    public void visit(FloatTypeDefinition def) throws VisitorException {
      this.typeName = isNullable ? "Integer" : "int";
      this.adapterName = "Int32Adapter";
    }

    @Override
    public void visit(LensSerialFloatTypeDefinition def) throws VisitorException {
      this.typeName = isNullable ? "Integer" : "int";
      this.adapterName = "Int32Adapter";
    }

  }

  DefinitionResolver resolver;
  final private HashMap<AUID, TypeMaker> typeCache = new HashMap<AUID, TypeMaker>();
  final private HashMap<AUID, TypeMaker> nullableTypeCache = new HashMap<AUID, TypeMaker>();
  File generatedSourcesDir;

  private ClassGenerator(MetaDictionaryCollection mds, File generatedSourcesDir) {
    this.resolver = mds;
    this.generatedSourcesDir = generatedSourcesDir;
  }

  private TypeMaker getTypeInformation(Definition def, boolean isNullabe) throws VisitorException {
    HashMap<AUID, TypeMaker> t = isNullabe ? nullableTypeCache : typeCache;
    TypeMaker tm = t.get(def.getIdentification());
    if (tm == null) {
      tm = this.new TypeMaker(isNullabe);
      def.accept(tm);
      t.put(def.getIdentification(), tm);
    }
    return tm;
  }

  private Collection<PropertyDefinition> getAllMembersOf(ClassDefinition definition) {
    ClassDefinition cdef = definition;

    ArrayList<PropertyDefinition> props = new ArrayList<>();

    while (cdef != null) {

      for (AUID auid : resolver.getMembersOf(cdef)) {
        props.add((PropertyDefinition) resolver.getDefinition(auid));
      }

      if (cdef.getParentClass() != null) {
        cdef = (ClassDefinition) resolver.getDefinition(cdef.getParentClass());
      } else {
        cdef = null;
      }

    }

    return props;
  }

  final static private  UL TYPE_DEFINITIONS = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.02000000");

  public static void generate(MetaDictionaryCollection mds, File generatedSourcesDir)
      throws IOException, URISyntaxException, VisitorException {

    ClassGenerator g = new ClassGenerator(mds, generatedSourcesDir);

    for (var md : mds.getDictionaries()) {

      if (md.getSchemeURI().toString().equals("http://www.ebu.ch/metadata/schemas/ebucore/smpte/class13/group"))
        continue;

      for (var def : md.getDefinitions()) {

        if (!(def instanceof ClassDefinition))
          continue;
        
        ClassDefinition classDef = (ClassDefinition) def;

        /* skip type definition classes */
        if (TYPE_DEFINITIONS.equalsWithMask(def.getIdentification(), 0b1111111111111000))
          continue;

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

          Definition typeDef = g.resolver.getDefinition(propertyDef.getType());

          TypeMaker t = g.getTypeInformation(typeDef, true);

          var member = new HashMap<String, String>();
          member.put("identification", propertyDef.getIdentification().toString());
          member.put("type", propertyDef.getType().toString());
          member.put("typeName", t.getTypeName());
          member.put("adapterName", t.getAdapterName());
          member.put("symbol", propertyDef.getSymbol());
          member.put("localIdentification", Integer.toString(propertyDef.getLocalIdentification()));
          member.put("isOptional", propertyDef.isOptional() ? "true" : "false");

          members.add(member);
        }

        data.put("members", members);

        g.generateSource(classTemplate, classDef.getSymbol(), data);

      }
    }
  }

  private void generateSource(Template template, String symbol, Object data) {
    try {
      var classFile = new File(generatedSourcesDir, symbol + ".java");
      var os = new FileWriter(classFile);
      os.write(template.apply(data));
      os.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to write class file", e);
    }
  }
}
