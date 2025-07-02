/*
 * Copyright (c) Sandflow Consulting, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.mxf;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.commons.numbers.fraction.Fraction;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.sandflow.smpte.mxf.adapters.ASCIIStringAdapter;
import com.sandflow.smpte.mxf.adapters.AUIDAdapter;
import com.sandflow.smpte.mxf.adapters.BooleanAdapter;
import com.sandflow.smpte.mxf.adapters.DataValueAdapter;
import com.sandflow.smpte.mxf.adapters.Int16Adapter;
import com.sandflow.smpte.mxf.adapters.Int32Adapter;
import com.sandflow.smpte.mxf.adapters.Int64Adapter;
import com.sandflow.smpte.mxf.adapters.Int8Adapter;
import com.sandflow.smpte.mxf.adapters.LocalDateAdapter;
import com.sandflow.smpte.mxf.adapters.LocalDateTimeAdapter;
import com.sandflow.smpte.mxf.adapters.LocalTimeAdapter;
import com.sandflow.smpte.mxf.adapters.PrimaryPackageAdapter;
import com.sandflow.smpte.mxf.adapters.RationalAdapter;
import com.sandflow.smpte.mxf.adapters.UInt16Adapter;
import com.sandflow.smpte.mxf.adapters.UInt32Adapter;
import com.sandflow.smpte.mxf.adapters.UInt64Adapter;
import com.sandflow.smpte.mxf.adapters.UInt8Adapter;
import com.sandflow.smpte.mxf.adapters.ULAdapter;
import com.sandflow.smpte.mxf.adapters.UMIDAdapter;
import com.sandflow.smpte.mxf.adapters.UTF16StringAdapter;
import com.sandflow.smpte.mxf.adapters.UTF8StringAdapter;
import com.sandflow.smpte.mxf.adapters.UUIDAdapter;
import com.sandflow.smpte.mxf.adapters.VersionAdapter;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.register.EssenceKeyRegister;
import com.sandflow.smpte.register.LabelsRegister;
import com.sandflow.smpte.register.LabelsRegister.Entry.Kind;
import com.sandflow.smpte.register.exceptions.DuplicateEntryException;
import com.sandflow.smpte.regxml.dict.DefinitionResolver;
import com.sandflow.smpte.regxml.dict.MetaDictionary;
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
import com.sandflow.smpte.regxml.dict.exceptions.IllegalDefinitionException;
import com.sandflow.smpte.regxml.dict.exceptions.IllegalDictionaryException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

import jakarta.xml.bind.JAXBException;

public class ClassGenerator {
  static final Handlebars handlebars = new Handlebars();
  static final Template classTemplate;
  static final Template enumerationTemplate;
  static final Template recordTemplate;
  static final Template classFactoryTemplate;
  static final Template variableArrayTemplate;
  static final Template fixedArrayTemplate;
  static final Template labelsTemplate;
  static final Template localTagsTemplate;
  static final Template essenceKeysTemplate;

  static {
    try {
      classTemplate = handlebars.compile("hbs/Class.java");
      enumerationTemplate = handlebars.compile("hbs/Enumeration.java");
      recordTemplate = handlebars.compile("hbs/Record.java");
      classFactoryTemplate = handlebars.compile("hbs/ClassFactoryInitializer.java");
      fixedArrayTemplate = handlebars.compile("hbs/FixedArray.java");
      variableArrayTemplate = handlebars.compile("hbs/VariableArray.java");
      labelsTemplate = handlebars.compile("hbs/Labels.java");
      localTagsTemplate = handlebars.compile("hbs/StaticLocalTagsInitializer.java");
      essenceKeysTemplate = handlebars.compile("hbs/EssenceKeys.java");
    } catch (Exception e) {
      throw new RuntimeException("Failed to load template", e);
    }
  }

  static final String TYPE_PACKAGE_NAME = "com.sandflow.smpte.mxf.types";
  static final String ADAPTER_PACKAGE_NAME = "com.sandflow.smpte.mxf.adapters";

  Definition findBaseDefinition(Definition definition) {

    while (definition instanceof RenameTypeDefinition) {
      definition = resolver.getDefinition(((RenameTypeDefinition) definition).getRenamedType());
    }

    return definition;
  }

  class TypeMaker extends NullDefinitionVisitor {
    /* TODO: handle byte ordering */
    private static final UL UUID_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01030300.00000000");
    private static final UL J2KExtendedCapabilities_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.03010d00.00000000");
    private static final UL Character_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100100.00000000");
    private static final UL Char_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100300.00000000");
    private static final UL UTF8Character_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01100500.00000000");
    private static final UL ProductReleaseType_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.02010101.00000000");
    private static final UL PrimaryPackage_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010104.06010104.01080000");
    private static final AUID UINT16_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.01010200.00000000");
    private static final AUID AUID_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.01030100.00000000");
    private static final UL METADEFINITIONS_UL = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.02000000");
    private static final AUID INTERCHANGE_OBJECT_AUID = AUID
        .fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01010100");
    private static final AUID DateStruct_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.03010500.00000000");
    private static final AUID PackageIDType_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.01030200.00000000");
    private static final AUID Rational_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.03010100.00000000");
    private static final AUID TimeStruct_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.03010600.00000000");
    private static final AUID TimeStamp_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.03010700.00000000");
    private static final AUID VersionType_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01040101.03010300.00000000");
    private static final UL ObjectClass_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010104.01010000");

    String typeName;
    String adapterName;
    Class<?> primitiveType;

    public String getTypeName() {
      return typeName;
    }

    public String getAdapterName() {
      return adapterName;
    }

    public Class<?> getPrimitiveType() {
      return primitiveType;
    }

    @Override
    public void visit(ClassDefinition def) throws VisitorException {

      /* TODO: how to prevent circular references */

      /* skip definition classes */
      if (METADEFINITIONS_UL.equalsWithMask(def.getIdentification(), 0b1111_1010_1111_1000))
        throw new VisitorException("Skipping definition classes");

      var data = new HashMap<String, Object>();

      data.put("className", def.getSymbol());
      data.put("identification", def.getIdentification().toString());
      if (!def.isConcrete()) {
        data.put("isAbstract", "1");
      }
      data.put("description", def.getDescription());

      AUID parentClassID = def.getParentClass();
      if (parentClassID != null) {
        var parentClass = (ClassDefinition) resolver.getDefinition(def.getParentClass());

        data.put("parentClassName", parentClass.getSymbol());
      }

      var members = new LinkedList<HashMap<String, String>>();

      ClassDefinition c = def;
      while (true) {
        for (var propertyAUID : resolver.getMembersOf(c)) {
          /* TODO: separate members into inherited and owned */

          PropertyDefinition propertyDef = (PropertyDefinition) resolver.getDefinition(propertyAUID);
          if (propertyDef == null) {
            throw new RuntimeException("Failed to find property definition for " + propertyAUID);
          }

          /* ignore definitions */
          if (ObjectClass_UL.equalsIgnoreVersion(propertyAUID))
            continue;

          try {
            Definition typeDef = findBaseDefinition(resolver.getDefinition(propertyDef.getType()));

            var member = new HashMap<String, String>();
            member.put("identification", propertyDef.getIdentification().toString());
            member.put("description", propertyDef.getDescription());
            member.put("type", propertyDef.getType().toString());
            member.put("typeDefinition", typeDef.getSymbol());
            member.put("symbol", propertyDef.getSymbol());
            member.put("localIdentification", Integer.toString(propertyDef.getLocalIdentification()));
            if (propertyDef.isOptional()) {
              member.put("isOptional", "true");
            }

            /* TODO: this is pretty ugly */
            if (c == def) {
              TypeMaker t = getTypeInformation(typeDef);
              member.put("typeName", t.getTypeName());
              if (PrimaryPackage_UL.equalsIgnoreVersion(propertyAUID)) {
                member.put("adapterName", PrimaryPackageAdapter.class.getName());
              } else {
                member.put("adapterName", t.getAdapterName());
              }
            } else {
              member.put("isInherited", "true");
            }

            members.addFirst(member);

          } catch (Exception e) {
            System.out.println("Skipping %s because of %s".formatted(propertyDef.getSymbol(), e.getMessage()));
            continue;
          }
        }

        /* skip non-header-classes */
        if (c.getParentClass() == null) {
          if (!INTERCHANGE_OBJECT_AUID.equals(c.getIdentification())) {
            /* TODO: do not throw an exception here */
            throw new VisitorException("Skipping non Header Metadata classes");
          }
          break;
        }
        c = (ClassDefinition) resolver.getDefinition(c.getParentClass());
      }
      data.put("members", members);

      generateSource(classTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), data);

      classList.add(def);

      this.typeName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
      this.adapterName = this.typeName;
    }

    @Override
    public void visit(CharacterTypeDefinition def) throws VisitorException {
      this.typeName = "String";

      if (def.getIdentification().equals(Character_UL)) {
        this.adapterName = UTF16StringAdapter.class.getName();
      } else if (def.getIdentification().equals(Char_UL)) {
        this.adapterName = ASCIIStringAdapter.class.getName();
      } else if (def.getIdentification().equals(UTF8Character_UL)) {
        this.adapterName = UTF8StringAdapter.class.getName();
      } else {
        throw new VisitorException("Unknown character type " + def.getIdentification());
      }
    }

    @Override
    public void visit(IntegerTypeDefinition def) throws VisitorException {
      if (def.isSigned()) {
        switch (def.getSize()) {
          case ONE:
            this.typeName = "Byte";
            this.primitiveType = byte.class;
            this.adapterName = Int8Adapter.class.getName();
            break;
          case TWO:
            this.typeName = "Short";
            this.primitiveType = short.class;
            this.adapterName = Int16Adapter.class.getName();
            break;
          case FOUR:
            this.typeName = "Integer";
            this.primitiveType = int.class;
            this.adapterName = Int32Adapter.class.getName();
            break;
          case EIGHT:
            this.typeName = "Long";
            this.primitiveType = long.class;
            this.adapterName = Int64Adapter.class.getName();
            break;
          default:
            throw new VisitorException("Unknown integer type " + def.getIdentification());
        }
      } else {
        switch (def.getSize()) {
          case ONE:
            this.typeName = "Short";
            this.primitiveType = short.class;
            this.adapterName = UInt8Adapter.class.getName();
            break;
          case TWO:
            this.typeName = "Integer";
            this.primitiveType = int.class;
            this.adapterName = UInt16Adapter.class.getName();
            break;
          case FOUR:
            this.typeName = "Long";
            this.primitiveType = long.class;
            this.adapterName = UInt32Adapter.class.getName();
            break;
          case EIGHT:
            this.typeName = "Long";
            this.primitiveType = long.class;
            this.adapterName = UInt64Adapter.class.getName();
            break;
          default:
            throw new VisitorException("Unknown integer type " + def.getIdentification());
        }
      }
    }

    @Override
    public void visit(ExtendibleEnumerationTypeDefinition def) throws VisitorException {
      this.typeName = "com.sandflow.smpte.util.UL";
      this.adapterName = ULAdapter.class.getName();
    }

    private final static UL BOOLEAN_TYPE = UL.fromURN("urn:smpte:ul:060e2b34.01040101.01040100.00000000");

    @Override
    public void visit(EnumerationTypeDefinition def) throws VisitorException {
      if (BOOLEAN_TYPE.equals(def.getIdentification())) {
        this.adapterName = BooleanAdapter.class.getName();
        this.typeName = "Boolean";
        return;
      }

      TypeMaker tm;
      if (ProductReleaseType_UL.equalsIgnoreVersion(def.getIdentification())) {
        /*
         * EXCEPTION: ProductReleaseType_UL is listed as
         * a UInt8 enum but encoded as a UInt16
         */
        tm = getTypeInformation(resolver.getDefinition(UINT16_AUID));
      } else {
        tm = getTypeInformation(findBaseDefinition(resolver.getDefinition(def.getElementType())));
      }

      var templateData = new HashMap<String, Object>();
      templateData.put("symbol", def.getSymbol());
      templateData.put("description", def.getDescription());
      templateData.put("valuesTypeName", tm.getTypeName());
      templateData.put("valuesAdapterName", tm.getAdapterName());
      templateData.put("valuesPrimitiveName", tm.getPrimitiveType().getSimpleName());

      var valuesData = new ArrayList<HashMap<String, String>>();
      templateData.put("values", valuesData);

      for (var value : def.getElements()) {
        var valueData = new HashMap<String, String>();
        valueData.put("name", value.getName());
        valueData.put("description", value.getDescription());
        valueData.put("value", Integer.toString(value.getValue()));
        valuesData.add(valueData);
      }

      generateSource(enumerationTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), templateData);

      this.adapterName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
      this.typeName = this.adapterName;
    }

    @Override
    public void visit(FixedArrayTypeDefinition def) throws VisitorException {
      if (UUID_UL.equalsIgnoreVersion(def.getIdentification())) {
        this.typeName = UUID.class.getName();
        this.adapterName = UUIDAdapter.class.getName();
        return;
      }

      var templateData = new HashMap<String, Object>();

      templateData.put("adapterName", def.getSymbol());
      templateData.put("itemCount", def.getElementCount());

      TypeMaker tm = getTypeInformation(findBaseDefinition(resolver.getDefinition(def.getElementType())));
      templateData.put("itemTypeName", tm.getTypeName());
      templateData.put("itemAdapterName", tm.getAdapterName());

      generateSource(fixedArrayTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), templateData);

      this.adapterName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
      this.typeName = this.adapterName;
    }

    @Override
    public void visit(IndirectTypeDefinition def) throws VisitorException {
      throw new VisitorException("IndirectTypesDefinition");
    }

    @Override
    public void visit(OpaqueTypeDefinition def) throws VisitorException {
      throw new VisitorException("OpaqueTypeDefinition");
    }

    @Override
    public void visit(RecordTypeDefinition def) throws VisitorException {
      if (def.getIdentification().equals(AUID_AUID)) {

        this.adapterName = AUIDAdapter.class.getName();
        this.typeName = AUID.class.getName();

      } else if (def.getIdentification().equals(DateStruct_AUID)) {

        this.adapterName = LocalDateAdapter.class.getName();
        this.typeName = LocalDate.class.getName();

      } else if (def.getIdentification().equals(PackageIDType_AUID)) {
        this.adapterName = UMIDAdapter.class.getName();
        this.typeName = UMID.class.getName();

      } else if (def.getIdentification().equals(Rational_AUID)) {

        this.adapterName = RationalAdapter.class.getName();
        this.typeName = Fraction.class.getName();

      } else if (def.getIdentification().equals(TimeStruct_AUID)) {

        this.adapterName = LocalTimeAdapter.class.getName();
        this.typeName = LocalTime.class.getName();

      } else if (def.getIdentification().equals(TimeStamp_AUID)) {

        this.adapterName = LocalDateTimeAdapter.class.getName();
        this.typeName = LocalDateTime.class.getName();

      } else if (def.getIdentification().equals(VersionType_AUID)) {

        this.adapterName = VersionAdapter.class.getName();
        this.typeName = Version.class.getName();

      } else {

        var templateData = new HashMap<String, Object>();
        templateData.put("name", def.getSymbol());

        if (J2KExtendedCapabilities_UL.equalsIgnoreVersion(def.getIdentification())) {
          /* Exception: Record with variable length fields */
          templateData.put("isVariableLength", "true");
        }

        var membersData = new ArrayList<HashMap<String, String>>();
        templateData.put("members", membersData);

        for (var member : def.getMembers()) {
          Definition memberTypeDef = findBaseDefinition(resolver.getDefinition(member.getType()));
          if (memberTypeDef == null) {
            throw new RuntimeException(
                String.format("Bad type %s at member %s.", member.getType().toString(), member.getName()));
          }
          TypeMaker tm = getTypeInformation(memberTypeDef);
          var valueData = new HashMap<String, String>();
          valueData.put("memberAdapterName", tm.getAdapterName());
          valueData.put("memberName", member.getName());
          valueData.put("memberTypeName", tm.getTypeName());
          membersData.add(valueData);
        }

        generateSource(recordTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), templateData);

        this.typeName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
        this.adapterName = this.typeName;
      }
    }

    @Override
    public void visit(RenameTypeDefinition def) throws VisitorException {
      TypeMaker tm = getTypeInformation(findBaseDefinition(resolver.getDefinition(def.getRenamedType())));

      this.typeName = tm.typeName;
      this.adapterName = tm.adapterName;
    }

    @Override
    public void visit(SetTypeDefinition def) throws VisitorException {
      /*
       * TODO: essentially the same as variable array, but need to check for
       * uniqueness?
       */
      var templateData = new HashMap<String, Object>();

      templateData.put("adapterName", def.getSymbol());

      Definition itemDef = findBaseDefinition(resolver.getDefinition(def.getElementType()));
      TypeMaker tm = getTypeInformation(itemDef);
      templateData.put("itemTypeName", tm.getTypeName());
      templateData.put("itemAdapterName", tm.getAdapterName());

      generateSource(variableArrayTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), templateData);

      this.adapterName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
      this.typeName = this.adapterName;
    }

    @Override
    public void visit(StreamTypeDefinition def) throws VisitorException {
      throw new VisitorException("StreamTypeDefinition");

    }

    @Override
    public void visit(StrongReferenceTypeDefinition def) throws VisitorException {
      ClassDefinition cdef = (ClassDefinition) findBaseDefinition(resolver.getDefinition(def.getReferencedType()));

      TypeMaker tm = getTypeInformation(cdef);

      this.typeName = tm.getTypeName();
      this.adapterName = tm.getAdapterName();
    }

    @Override
    public void visit(StringTypeDefinition def) throws VisitorException {
      this.typeName = "String";

      Definition chrdef = findBaseDefinition(resolver.getDefinition(def.getElementType()));

      if (chrdef.getIdentification().equals(Character_UL)) {
        this.adapterName = UTF16StringAdapter.class.getName();
      } else if (chrdef.getIdentification().equals(Char_UL)) {
        this.adapterName = ASCIIStringAdapter.class.getName();
      } else if (chrdef.getIdentification().equals(UTF8Character_UL)) {
        this.adapterName = UTF8StringAdapter.class.getName();
      } else {
        throw new VisitorException("Unknown character type " + def.getIdentification());
      }
    }

    private static final UL IndexEntryArray_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.04020700.00000000");
    private static final UL DeltaEntryArray_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.04020800.00000000");
    private static final UL DataValue_UL = UL.fromURN("urn:smpte:ul:060e2b34.01040101.04100100.00000000");

    @Override
    public void visit(VariableArrayTypeDefinition def) throws VisitorException {
      Definition itemDef = findBaseDefinition(resolver.getDefinition(def.getElementType()));

      if (itemDef instanceof CharacterTypeDefinition || itemDef.getName().contains("StringArray")) {
        throw new VisitorException("StringArray not supported: " + def.getIdentification());
      }

      if (DeltaEntryArray_UL.equalsIgnoreVersion(def.getIdentification())) {
        this.adapterName = "com.sandflow.smpte.mxf.types.DeltaEntryArray";
        this.typeName = "com.sandflow.smpte.mxf.types.DeltaEntryArray";
      } else if (IndexEntryArray_UL.equalsIgnoreVersion(def.getIdentification())) {
        this.adapterName = "com.sandflow.smpte.mxf.types.IndexEntryArray";
        this.typeName = "com.sandflow.smpte.mxf.types.IndexEntryArray";
      } else if (DataValue_UL.equalsIgnoreVersion(def.getIdentification())) {
        this.adapterName = DataValueAdapter.class.getName();
        this.typeName = "byte[]";
      } else {
        var templateData = new HashMap<String, Object>();

        templateData.put("adapterName", def.getSymbol());

        TypeMaker tm = getTypeInformation(itemDef);
        templateData.put("itemTypeName", tm.getTypeName());
        templateData.put("itemAdapterName", tm.getAdapterName());

        generateSource(variableArrayTemplate, TYPE_PACKAGE_NAME, def.getSymbol(), templateData);

        this.adapterName = TYPE_PACKAGE_NAME + "." + def.getSymbol();
        this.typeName = this.adapterName;
      }
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

      TypeMaker tm = getTypeInformation(resolver.getDefinition(uniquepropdef.getType()));

      this.typeName = tm.typeName;
      this.adapterName = tm.adapterName;
    }

    @Override
    public void visit(FloatTypeDefinition def) throws VisitorException {
      throw new VisitorException("Floating point types not supported: " + def.getSymbol());
    }

    @Override
    public void visit(LensSerialFloatTypeDefinition def) throws VisitorException {
      throw new VisitorException("LensSerialFloatTypeDefinition");

    }

  }

  DefinitionResolver resolver;
  final private HashMap<AUID, TypeMaker> typeCache = new HashMap<AUID, TypeMaker>();
  final private ArrayList<ClassDefinition> classList = new ArrayList<ClassDefinition>();
  File generatedSourcesDir;

  private ClassGenerator(MetaDictionaryCollection mds, File generatedSourcesDir) {
    this.resolver = mds;
    this.generatedSourcesDir = generatedSourcesDir;
  }

  private TypeMaker getTypeInformation(Definition def) throws VisitorException {
    TypeMaker tm = typeCache.get(def.getIdentification());
    if (tm == null) {
      tm = this.new TypeMaker();
      def.accept(tm);
      typeCache.put(def.getIdentification(), tm);
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

  public static void generate(MetaDictionaryCollection mds, LabelsRegister lr, EssenceKeyRegister ekr,
      File generatedSourcesDir)
      throws IOException, URISyntaxException, VisitorException {

    final ArrayList<PropertyDefinition> propList = new ArrayList<PropertyDefinition>();

    ClassGenerator g = new ClassGenerator(mds, generatedSourcesDir);

    for (var md : mds.getDictionaries()) {

      if (md.getSchemeURI().toString().equals("http://www.ebu.ch/metadata/schemas/ebucore/smpte/class13/group"))
        continue;

      for (var def : md.getDefinitions()) {
        try {
          if (def instanceof ClassDefinition) {
            g.getTypeInformation(def);
          } else if (def instanceof PropertyDefinition) {
            var propDef = (PropertyDefinition) def;
            if (propDef.getLocalIdentification() != 0) {
              propList.add(propDef);
            }
          }
        } catch (Exception e) {
          /* TODO: log */
        }
      }
    }

    /* generate the class factory */
    g.generateSource(classFactoryTemplate, "com.sandflow.smpte.mxf", "ClassFactoryInitializer", g.classList);

    /* generate the static local tags */
    g.generateSource(localTagsTemplate, "com.sandflow.smpte.mxf", "StaticLocalTagsInitializer", propList);

    /* generate labels */
    g.generateSource(
        labelsTemplate,
        "com.sandflow.smpte.mxf",
        "Labels",
        lr.getEntries().stream().filter(e -> e.getKind() == Kind.LEAF).toArray());

    /* generate EssenceKeys */
    g.generateSource(
        essenceKeysTemplate,
        "com.sandflow.smpte.mxf",
        "EssenceKeys",
        ekr.getEntries().stream().filter(e -> e.getKind() == EssenceKeyRegister.Entry.Kind.LEAF).toArray());
  }

  private void generateSource(Template template, String packageName, String symbol, Object data) {
    try {
      var classDir = new File(generatedSourcesDir, packageName.replace(".", "/"));
      if (!classDir.exists()) {
        classDir.mkdirs();
      }
      var classFile = new File(classDir, symbol + ".java");
      var os = new FileWriter(classFile);
      os.write(template.apply(data));
      os.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to write class file", e);
    }
  }

  private static void deleteFile(File f) {
    if (f.isDirectory()) {
      for (File nf : f.listFiles()) {
        deleteFile(nf);
      }
    }
    f.delete();
  }

  public static void main(String[] args) throws URISyntaxException, IllegalDictionaryException, JAXBException,
      IOException, IllegalDefinitionException, VisitorException, DuplicateEntryException {
    File dir = new File(args[0]);

    File[] mdFiles = dir.listFiles(
        new FilenameFilter() {

          @Override
          public boolean accept(File dir, String name) {
            return name.endsWith(".xml");
          }
        });

    var mds = new MetaDictionaryCollection();
    for (var mdFile : mdFiles) {
      var fr = new FileReader(mdFile);
      mds.addDictionary(MetaDictionary.fromXML(fr));
    }

    File generatedClassDir = new File(args[1]);
    if (!generatedClassDir.exists()) {
      generatedClassDir.mkdirs();
    } else {
      deleteFile(generatedClassDir);
    }

    /* Labels register */

    final FileReader labelreader = new FileReader(args[2]);
    final LabelsRegister lr = LabelsRegister.fromXML(labelreader);
    labelreader.close();

    /* Essence Keys register */

    final FileReader ekrreader = new FileReader(args[3]);
    final EssenceKeyRegister ekr = EssenceKeyRegister.fromXML(ekrreader);
    ekrreader.close();

    /* generate the source files */

    ClassGenerator.generate(mds, lr, ekr, generatedClassDir);
  }
}
