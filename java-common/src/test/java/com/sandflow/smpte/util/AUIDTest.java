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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AUIDTest {

  @Test
  void testFromURN() {
    String ulURN = "urn:smpte:ul:060e2b34.01010101.0d010201.01000000";
    AUID fromUL = AUID.fromURN(ulURN);
    assertNotNull(fromUL);
    assertTrue(fromUL.isUL());
    assertEquals(ulURN, fromUL.toString());

    String uuidURN = "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
    AUID fromUUID = AUID.fromURN(uuidURN);
    assertNotNull(fromUUID);
    assertTrue(fromUUID.isUUID());
    assertEquals(uuidURN, fromUUID.toString());

    assertNull(AUID.fromURN(null));
    assertNull(AUID.fromURN("invalid:urn"));
  }

  @Test
  void testUUIDConstructorAndConversion() {
    byte[] uuidBytes = new byte[] {
        (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
        (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
        (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
        (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
    };
    UUID originalUUID = new UUID(uuidBytes);

    AUID auidFromUUID = new AUID(originalUUID);

    byte[] expectedAUIDBytes = new byte[] {
        (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
        (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff,
        (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03,
        (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07
    };
    assertArrayEquals(expectedAUIDBytes, auidFromUUID.getBytes());

    assertTrue(auidFromUUID.isUUID());
    assertFalse(auidFromUUID.isUL());

    UUID convertedBackUUID = auidFromUUID.asUUID();
    assertNotNull(convertedBackUUID);
    assertArrayEquals(uuidBytes, convertedBackUUID.getBytes());
    assertEquals(originalUUID, convertedBackUUID);
  }

  @Test
  void testULConstructorAndConversion() {
    byte[] ulBytes = new byte[] {
        (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
        (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
        (byte) 0x0d, (byte) 0x01, (byte) 0x02, (byte) 0x01,
        (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };
    UL originalUL = new UL(ulBytes);

    AUID auidFromUL = new AUID(originalUL);

    assertArrayEquals(ulBytes, auidFromUL.getBytes());
    assertTrue(auidFromUL.isUL());
    assertFalse(auidFromUL.isUUID());

    UL convertedBackUL = auidFromUL.asUL();
    assertNotNull(convertedBackUL);
    assertEquals(originalUL, convertedBackUL);

    assertNull(auidFromUL.asUUID());
  }

  @Test
  void testAsULOnUUIDBasedAUID() {
    AUID auidFromUUID = new AUID(UUID.fromRandom());
    assertTrue(auidFromUUID.isUUID());
    assertThrows(IllegalArgumentException.class, auidFromUUID::asUL);
  }

  @Test
  void testEqualsAndHashCode() {
    UL ul1 = UL.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01000000");
    UL ul2 = UL.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01000000");
    UL ul3 = UL.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01000001");

    AUID auid1 = new AUID(ul1);
    AUID auid2 = new AUID(ul2);
    AUID auid3 = new AUID(ul3);

    assertEquals(auid1, auid1);
    assertEquals(auid1, auid2);
    assertEquals(auid2, auid1);
    assertNotEquals(null, auid1);
    assertNotEquals("a string", auid1);
    assertNotEquals(auid1, auid3);

    assertEquals(auid1.hashCode(), auid2.hashCode());

    assertTrue(auid1.equals(ul1));
    assertTrue(auid1.equals(ul2));
    assertFalse(auid1.equals(ul3));
  }

  @Test
  void testGetBytesImmutability() {
    byte[] originalBytes = new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
    AUID auid = new AUID(originalBytes);

    byte[] retrievedBytes = auid.getBytes();
    assertArrayEquals(originalBytes, retrievedBytes);

    retrievedBytes[0] = (byte) 0xFF;

    assertNotEquals(retrievedBytes[0], auid.getBytes()[0], "Internal state of AUID should not be mutable.");
  }

  @Test
  void testMakeVersionNormalized() {
    AUID auidUL = AUID.fromURN("urn:smpte:ul:060e2b34.01010101.0d010201.01000001");
    AUID normalizedAuidUL = auidUL.makeVersionNormalized();
    assertEquals((byte) 0, normalizedAuidUL.asUL().getOctet(7));

    AUID auidUUID = AUID.fromURN("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
    AUID normalizedAuidUUID = auidUUID.makeVersionNormalized();
    assertEquals(auidUUID, normalizedAuidUUID);
  }
}