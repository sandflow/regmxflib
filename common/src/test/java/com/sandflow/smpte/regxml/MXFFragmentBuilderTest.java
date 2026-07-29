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
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import static com.sandflow.smpte.regxml.dict.importers.RegisterImporter.fromRegister;
import com.sandflow.smpte.util.UL;
import com.sandflow.util.events.Event;
import com.sandflow.util.events.EventHandler;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares RegXML Fragments generated from sample MXF files against golden
 * reference RegXML Fragments.
 */
public class MXFFragmentBuilderTest {

  private final static Logger LOG = Logger.getLogger(MXFFragmentBuilderTest.class.getName());

  private static final UL PREFACE_KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01012f00");

  private final static String registers_dir_path = "registers";
  private final static String mxf_files_dir_path = "mxf-files";
  private final static String ref_files_dir_path = "regxml-files";

  private static MetaDictionaryCollection mds;
  private static DocumentBuilder db;

  static Iterable<String> data() throws URISyntaxException {

    File ref_files_dir = new File(ClassLoader.getSystemResource(ref_files_dir_path).toURI());

    return List.of(ref_files_dir.list());
  }

  @BeforeAll
  static void loadDictionaries() throws Exception {

    final String register_dir = registers_dir_path + "/snapshot/";

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

    EventHandler dictevthandler = new EventHandler() {

      @Override
      public boolean handle(Event evt) {

        String msg = evt.getCode().getClass().getCanonicalName() + "::" + evt.getCode().toString() + " "
            + evt.getMessage();

        switch (evt.getSeverity()) {
          case FATAL:
            LOG.severe(msg);
            break;
          case ERROR:
          case INFO:
          case WARN:
            break;
        }
        return true;
      }
    };

    mds = fromRegister(treg, greg, ereg, dictevthandler);
    assertNotNull(mds);

    /* setup the doc builder */

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setCoalescing(true);
    dbf.setIgnoringElementContentWhitespace(true);
    dbf.setIgnoringComments(true);

    db = dbf.newDocumentBuilder();
    assertNotNull(db);
  }

  @ParameterizedTest(name = "Test file {0}")
  @MethodSource("data")
  void testGeneratedAgainstReference(String ref_file_name) throws Exception {

    /* get the sample files */
    final String mxf_file_name = ref_file_name.substring(0, ref_file_name.lastIndexOf('.')) + ".mxf";

    InputStream sampleis = ClassLoader.getSystemResourceAsStream(mxf_files_dir_path + "/" + mxf_file_name);

    assertNotNull(sampleis);

    /* build the regxml fragment */
    Document gendoc = db.newDocument();

    assertNotNull(gendoc);

    EventHandler evthandler = new EventHandler() {

      @Override
      public boolean handle(Event evt) {

        String msg = evt.getCode().getClass().getCanonicalName() + "::" + evt.getCode().toString() + " "
            + evt.getMessage();

        switch (evt.getSeverity()) {
          case FATAL:
            LOG.severe(msg);
            return false;
          case INFO:
            LOG.info(msg);
            break;
          case ERROR:
          case WARN:
            LOG.warning(msg);
        }
        return true;
      }
    };

    DocumentFragment gendf = MXFFragmentBuilder.fromInputStream(sampleis, mds, null, evthandler, PREFACE_KEY, gendoc);

    assertNotNull(gendf);

    gendoc.appendChild(gendf);

    /* load the reference document */

    InputStream refis = ClassLoader.getSystemResourceAsStream(ref_files_dir_path + "/" + ref_file_name);
    assertNotNull(refis);

    Document refdoc = db.parse(refis);
    assertNotNull(refdoc);

    /* compare the ref vs the generated */
    assertTrue(compareDOMElement(gendoc.getDocumentElement(), refdoc.getDocumentElement()));

  }

  static Map<String, String> getAttributes(Element e) {

    NodeList nl = e.getChildNodes();
    HashMap<String, String> m = new HashMap<>();

    for (int i = 0; i < nl.getLength(); i++) {

      if (nl.item(i).getNodeType() == Node.ATTRIBUTE_NODE) {
        m.put(nl.item(i).getNodeName(), nl.item(i).getNodeValue());
      }

    }

    return m;
  }

  static List<Element> getElements(Element e) {

    NodeList nl = e.getChildNodes();
    ArrayList<Element> m = new ArrayList<>();

    for (int i = 0; i < nl.getLength(); i++) {

      if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
        m.add((Element) nl.item(i));
      }

    }

    return m;
  }

  static String getFirstTextNodeText(Element e) {
    for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.TEXT_NODE) {
        return n.getNodeValue();
      }
    }

    return "";
  }

  static boolean compareDOMElement(Element el1, Element el2) {

    List<Element> elems1 = getElements(el1);
    List<Element> elems2 = getElements(el2);

    if (elems1.size() != elems2.size()) {

      System.out.println(
          String.format(
              "Sub element count of %s does not match reference.",
              el1.getLocalName()));

      System.out.println("Left:");
      System.out.println(elems1);
      System.out.println("Right:");
      System.out.println(elems2);

      return false;
    }

    Map<String, String> attrs1 = getAttributes(el1);
    Map<String, String> attrs2 = getAttributes(el2);

    for (Entry<String, String> entry : attrs1.entrySet()) {
      if (!entry.getValue().equals(attrs2.get(entry.getKey()))) {

        System.out.println(
            String.format(
                "Attribute %s with value %s does not match reference.",
                entry.getKey(),
                entry.getValue()));

        return false;
      }
    }

    for (int i = 0; i < elems1.size(); i++) {

      if (!elems1.get(i).getNodeName().equals(elems2.get(i).getNodeName())) {

        System.out.println(
            String.format(
                "Element %s does not match reference.",
                elems1.get(i).getNodeName()));

        return false;
      }

      String txt1 = getFirstTextNodeText(elems1.get(i)).trim();
      String txt2 = getFirstTextNodeText(elems2.get(i)).trim();

      if (!txt1.equals(txt2)) {
        System.out.println(
            String.format(
                "Text content at %s ('%s') does not match reference ('%s')",
                elems1.get(i).getNodeName(),
                txt1,
                txt2));
        return false;
      }

      if (!compareDOMElement(elems1.get(i), elems2.get(i))) {
        return false;
      }
    }

    return true;

  }

}
