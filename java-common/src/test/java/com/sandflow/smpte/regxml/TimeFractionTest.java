package com.sandflow.smpte.regxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.sandflow.smpte.klv.Group;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.regxml.dict.MetaDictionaryCollection;
import com.sandflow.smpte.regxml.dict.definitions.RecordTypeDefinition;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.Event;

public class TimeFractionTest {
  private static final String TIMESTRUCT_URN = "urn:smpte:ul:060e2b34.01040101.03010600.00000000";
  private static final String TIMESTAMP_URN = "urn:smpte:ul:060e2b34.01040101.03010700.00000000";

  private final List<Event> events = new ArrayList<>();

  private Element apply(String urn, byte... value) throws Exception {
    FragmentBuilder fb = new FragmentBuilder(new MetaDictionaryCollection(), new HashMap<UUID, Group>(), null, evt -> {
      events.add(evt);
      return true;
    });

    RecordTypeDefinition def = new RecordTypeDefinition();
    def.setIdentification(new AUID(UL.fromURN(urn)));
    def.setSymbol("TestRecord");

    Element element = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument().createElement("Test");

    fb.applyRule5_8(element, new MXFDataInput(new ByteArrayInputStream(value)), def);

    return element;
  }

  private static boolean hasComment(Element element) {
    for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.COMMENT_NODE) {
        return true;
      }
    }
    return false;
  }

  @Test
  void testTimeAtMaximum() throws Exception {
    Element e = apply(TIMESTRUCT_URN, (byte) 1, (byte) 2, (byte) 3, (byte) 249);

    assertEquals("01:02:03.996Z", e.getTextContent());
    assertEquals(0, events.size());
  }

  @Test
  void testTimeCapped() throws Exception {
    Element e = apply(TIMESTRUCT_URN, (byte) 1, (byte) 2, (byte) 3, (byte) 250);

    assertEquals("01:02:03.996Z", e.getTextContent());
    assertEquals(1, events.size());
    assertEquals(FragmentBuilder.EventCodes.INVALID_TIME_FRACTION, events.get(0).getCode());
    assertTrue(hasComment(e));
  }

  @Test
  void testTimeStampCapped() throws Exception {
    Element e = apply(TIMESTAMP_URN, (byte) 0x07, (byte) 0xEA, (byte) 10, (byte) 31, (byte) 1, (byte) 2, (byte) 3, (byte) 255);

    assertEquals("2026-10-31T01:02:03.996Z", e.getTextContent());
    assertEquals(1, events.size());
    assertEquals(FragmentBuilder.EventCodes.INVALID_TIME_FRACTION, events.get(0).getCode());
    assertTrue(hasComment(e));
  }
}
