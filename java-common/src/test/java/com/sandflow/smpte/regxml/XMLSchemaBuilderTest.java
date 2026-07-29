/*
 * Copyright (c) Sandflow Consulting LLC
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

package com.sandflow.smpte.regxml;

import com.sandflow.smpte.register.ElementsRegister;
import com.sandflow.smpte.register.GroupsRegister;
import com.sandflow.smpte.register.TypesRegister;
import com.sandflow.smpte.regxml.dict.MetaDictionary;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import static com.sandflow.smpte.regxml.dict.importers.RegisterImporter.fromRegister;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.logging.Logger;
import org.w3c.dom.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises XMLSchemaBuilder against every historical register snapshot
 * present under the "registers" test resource directory.
 */
public class XMLSchemaBuilderTest {

  private final static Logger LOG = Logger.getLogger(XMLSchemaBuilderTest.class.getName());

  private final static String registers_dir_path = "registers";

  static Iterable<String> data() throws URISyntaxException {
    File f = new File(ClassLoader.getSystemResource(XMLSchemaBuilderTest.registers_dir_path).toURI());

    return Arrays.asList(f.list((dir, name) -> new File(dir, name).isDirectory()));
  }

  @ParameterizedTest(name = "Release: {0}")
  @MethodSource("data")
  void testGenerateXMLSchema(String register_name) throws Exception {

    final String register_dir = XMLSchemaBuilderTest.registers_dir_path + "/" + register_name + "/";

    /* load the registers */

    Reader fe = new InputStreamReader(ClassLoader.getSystemResourceAsStream(register_dir + "Elements.xml"));
    assertNotNull(fe);

    Reader fg = new InputStreamReader(ClassLoader.getSystemResourceAsStream(register_dir + "Groups.xml"));
    assertNotNull(fg);

    Reader ft = new InputStreamReader(ClassLoader.getSystemResourceAsStream(register_dir + "Types.xml"));
    assertNotNull(ft);

    ElementsRegister ereg = ElementsRegister.fromXML(fe);
    assertNotNull(ereg);

    GroupsRegister greg = GroupsRegister.fromXML(fg);
    assertNotNull(greg);

    TypesRegister treg = TypesRegister.fromXML(ft);
    assertNotNull(treg);

    /* build the dictionaries */

    EventHandler evthandler = new EventHandler() {

      @Override
      public boolean handle(Event evt) {

        String msg = evt.getCode().getClass().getCanonicalName() + "::" + evt.getCode().toString() + " "
            + evt.getMessage();

        switch (evt.getSeverity()) {
          case ERROR:
          case FATAL:
            LOG.severe(msg);
            break;
          case INFO:
            LOG.info(msg);
            break;
          case WARN:
            LOG.warning(msg);
        }
        return true;
      }
    };

    MetaDictionaryCollection mds = fromRegister(treg, greg, ereg, evthandler);
    assertNotNull(mds);

    /* create the fragment builder */
    XMLSchemaBuilder sb = new XMLSchemaBuilder(
        mds,
        new EventHandler() {

          @Override
          public boolean handle(Event evt) {
            String msg = evt.getCode().getClass().getCanonicalName() + "::" + evt.getCode().toString() + " "
                + evt.getMessage();

            switch (evt.getSeverity()) {
              case ERROR:
              case FATAL:
                LOG.severe(msg);
                break;
              case INFO:
                LOG.info(msg);
                break;
              case WARN:
                LOG.warning(msg);
                break;
            }
            return true;
          }
        });

    for (MetaDictionary md : mds.getDictionaries()) {

      Document doc = sb.fromDictionary(md);

      assertNotNull(doc);
    }

  }

}
