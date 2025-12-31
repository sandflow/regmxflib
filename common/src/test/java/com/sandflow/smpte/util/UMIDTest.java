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

package com.sandflow.smpte.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UMIDTest {

  static final String TEST_UMID_URN = "urn:smpte:umid:060a2b34.01010105.01010f20.13000000.8a8a3da7.352d5387.be5c5753.a330c1bc";
  static final byte[] TEST_UMID_BYTES = {
      (byte) 0x06, (byte) 0x0a, (byte) 0x2b, (byte) 0x34,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05,
      (byte) 0x01, (byte) 0x01, (byte) 0x0f, (byte) 0x20,
      (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0x00,
      (byte) 0x8a, (byte) 0x8a, (byte) 0x3d, (byte) 0xa7,
      (byte) 0x35, (byte) 0x2d, (byte) 0x53, (byte) 0x87,
      (byte) 0xbe, (byte) 0x5c, (byte) 0x57, (byte) 0x53,
      (byte) 0xa3, (byte) 0x30, (byte) 0xc1, (byte) 0xbc
  };

  @Test
  void testFromURN() {
    UMID umid = UMID.fromURN(TEST_UMID_URN);
    assertNotNull(umid);
    assertArrayEquals(TEST_UMID_BYTES, umid.getBytes());
  }

  @Test
  void testFromURNWithUpperCase() {
    UMID umid = UMID.fromURN("urn:smpte:umid:060A2B34.01010105.01010F20.13000000.8A8A3DA7.352D5387.BE5C5753.A330C1BC");
    assertNotNull(umid);
    assertArrayEquals(TEST_UMID_BYTES, umid.getBytes());
  }

  @Test
  void testFromURNInvalid() {
    assertNull(UMID.fromURN("urn:smpte:umid:invalid"));
    assertNull(UMID.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01010100"));
    assertNull(UMID.fromURN("not-a-urn"));
    assertNull(UMID.fromURN(null));
  }

  @Test
  void testConstructorAndGetBytes() {
    UMID umid = new UMID(TEST_UMID_BYTES);
    assertArrayEquals(TEST_UMID_BYTES, umid.getBytes());

    /*Immutability check for constructor */
    byte[] original = TEST_UMID_BYTES.clone();
    UMID umid2 = new UMID(original);
    original[0] = (byte) 0xFF;
    assertNotEquals(original[0], umid2.getBytes()[0]);

    /* Immutability check for getBytes */
    byte[] retrieved = umid.getBytes();
    retrieved[0] = (byte) 0xFF;
    assertNotEquals(retrieved[0], umid.getBytes()[0]);
  }

  @Test
  void testEqualsAndHashCode() {
    UMID umid1 = UMID.fromURN(TEST_UMID_URN);
    UMID umid2 = new UMID(TEST_UMID_BYTES);
    UUID uuid = UUID.fromRandom();
    UMID umid3 = UMID.fromUUID(uuid);

    assertEquals(umid1, umid2);
    assertEquals(umid1.hashCode(), umid2.hashCode());

    assertNotEquals(umid1, umid3);

    assertNotEquals(null, umid1);
    assertNotEquals(umid1, "a string");
    assertEquals(umid1, umid1);
  }

  @Test
  void testToString() {
    UMID umid = new UMID(TEST_UMID_BYTES);
    assertEquals(TEST_UMID_URN, umid.toString());
  }

  @Test
  void testFromUUID() {
    UUID uuid = UUID.fromURN("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
    UMID umid = UMID.fromUUID(uuid);
    assertNotNull(umid);

    assertEquals("urn:smpte:umid:060a2b34.01010105.01010f20.13000000.f81d4fae.7dec11d0.a76500a0.c91e6bf6",
        umid.toString());
  }

  @Test
  void testNullUMID() {
    UMID nullUmid = UMID.NULL_UMID;
    assertNotNull(nullUmid);

    assertArrayEquals(new byte[32], nullUmid.getBytes());

    String expectedURN = "urn:smpte:umid:00000000.00000000.00000000.00000000.00000000.00000000.00000000.00000000";
    assertEquals(expectedURN, nullUmid.toString());

    UMID fromURN = UMID.fromURN(expectedURN);
    assertEquals(nullUmid, fromURN);
  }
}