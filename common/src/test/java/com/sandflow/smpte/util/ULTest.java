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
|* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
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

public class ULTest {

  static final String TEST_UL_0_URN = "urn:smpte:ul:060e2b34.01010105.01010d20.13000000";
  static final byte[] TEST_UL_0 = {
      (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05,
      (byte) 0x01, (byte) 0x01, (byte) 0x0d, (byte) 0x20,
      (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0x00
  };
  static final String TEST_UL_DOT_VALUE = "06.0e.2b.34.01.01.01.05.01.01.0d.20.13.00.00.00";

  static final byte[] TEST_UL_GROUP = {
      (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
      (byte) 0x02, (byte) 0x7f, (byte) 0x01, (byte) 0x01,
      (byte) 0x0d, (byte) 0x01, (byte) 0x01, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x2f, (byte) 0x00
  }; // Preface Set

  static final byte[] TEST_UL_LOCAL_SET = {
      (byte) 0x06, (byte) 0x0e, (byte) 0x2b, (byte) 0x34,
      (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01,
      (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x01
  };

  @Test
  public void testFromURN() {
    UL ul = UL.fromURN(TEST_UL_0_URN);
    assertNotNull(ul);
    assertArrayEquals(TEST_UL_0, ul.getBytes());
    assertEquals(TEST_UL_0_URN, ul.toString());
  }

  @Test
  public void testFromURNInvalid() {
    assertNull(UL.fromURN("urn:smpte:ul:invalid"));
    assertNull(UL.fromURN("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6"));
    assertNull(UL.fromURN(null));
  }

  @Test
  public void testFromDotValue() {
    UL ul = UL.fromDotValue(TEST_UL_DOT_VALUE);
    assertNotNull(ul);
    assertArrayEquals(TEST_UL_0, ul.getBytes());
  }

  @Test
  public void testFromDotValueInvalid() {
    assertNull(UL.fromDotValue("06.0e.2b.34"));
    assertNull(UL.fromDotValue(null));
  }

  @Test
  public void testConstructor() {
    UL ul = new UL(TEST_UL_0);
    assertArrayEquals(TEST_UL_0, ul.getBytes());

    assertThrows(IllegalArgumentException.class, () -> new UL(null));
    assertThrows(IllegalArgumentException.class, () -> new UL(new byte[15]));
  }

  @Test
  public void testGetBytesImmutability() {
    byte[] original = TEST_UL_0.clone();
    UL ul = new UL(original);
    original[0] = (byte) 0xFF;
    assertNotEquals(original[0], ul.getOctet(0));

    byte[] retrieved = ul.getBytes();
    retrieved[0] = (byte) 0xFF;
    assertNotEquals(retrieved[0], ul.getOctet(0));
  }

  @Test
  public void testIsGroup() {
    UL groupUL = new UL(TEST_UL_GROUP);
    assertTrue(groupUL.isGroup());

    UL itemUL = new UL(TEST_UL_0);
    assertFalse(itemUL.isGroup());
  }

  @Test
  public void testIsLocalSet() {
    UL localSetUL = new UL(TEST_UL_LOCAL_SET);
    assertTrue(localSetUL.isLocalSet());

    UL groupUL = new UL(TEST_UL_GROUP);
    assertFalse(groupUL.isLocalSet());
  }

  @Test
  public void testGetRegistryDesignator() {
    UL groupUL = new UL(TEST_UL_GROUP);
    assertEquals(0x7f, groupUL.getRegistryDesignator());
  }

  @Test
  public void testMakeVersionNormalized() {
    byte[] versionedBytes = TEST_UL_0.clone();
    versionedBytes[UL.VERSION_BYTE] = (byte) 0x10;
    UL versionedUL = new UL(versionedBytes);
    assertEquals((byte) 0x10, versionedUL.getVersion());

    UL normalizedUL = versionedUL.makeVersionNormalized();
    assertEquals((byte) 0x00, normalizedUL.getVersion());

    byte[] expectedNormalized = versionedBytes.clone();
    expectedNormalized[UL.VERSION_BYTE] = 0x00;
    assertArrayEquals(expectedNormalized, normalizedUL.getBytes());

    // test on already normalized
    assertSame(normalizedUL, normalizedUL.makeVersionNormalized());
  }

  @Test
  public void testEqualsIgnoreVersion() {
    byte[] bytes1 = TEST_UL_0.clone();
    bytes1[UL.VERSION_BYTE] = (byte) 0x05;
    UL ul1 = new UL(bytes1);

    byte[] bytes2 = TEST_UL_0.clone();
    bytes2[UL.VERSION_BYTE] = (byte) 0x08;
    UL ul2 = new UL(bytes2);

    byte[] bytes3 = TEST_UL_0.clone();
    bytes3[0] = (byte) 0xFF;
    UL ul3 = new UL(bytes3);

    assertTrue(ul1.equalsIgnoreVersion(ul2));
    assertFalse(ul1.equalsIgnoreVersion(ul3));
    assertFalse(ul1.equalsIgnoreVersion((UL) null));
  }

  @Test
  public void testEqualsWithMask() {
    UL ul1 = new UL(TEST_UL_0);

    byte[] bytes2 = ul1.getBytes();
    bytes2[10] = (byte) 0xFF;
    bytes2[8] = (byte) 0xFF;
    UL ul2 = new UL(bytes2);

    // ignore nothing
    assertFalse(ul1.equalsWithMask(ul2, 0xFFFF));
    assertTrue(ul1.equalsWithMask(ul1, 0xFFFF));

    // ignore byte 8
    assertFalse(ul1.equalsWithMask(ul2, 0b11111111_01111111));

    // ignore byte 10
    assertFalse(ul1.equalsWithMask(ul2, 0b11111111_11011111));

    // ignore bytes 10 and 8
    assertTrue(ul1.equalsWithMask(ul2, 0b11111111_01011111));
  }

  @Test
  public void testEqualsWithAUID() {
    UL ul = new UL(TEST_UL_0);
    AUID auidFromUL = new AUID(ul);
    AUID auidFromUUID = new AUID(UUID.fromRandom());

    assertTrue(ul.equals(auidFromUL));
    assertFalse(ul.equals(auidFromUUID));
    assertFalse(ul.equals((AUID) null));
  }

  @Test
  public void testEquals() {
    UL ul1 = new UL(TEST_UL_0);
    UL ul2 = new UL(TEST_UL_0);
    UL ul3 = new UL(TEST_UL_GROUP);

    assertTrue(ul1.equals(ul2));
    assertTrue(ul1.equals((Object) ul2));
    assertFalse(ul1.equals(ul3));
    assertFalse(ul1.equals((UL) null));
    assertFalse(ul1.equals(new Object()));
  }

  @Test
  public void testHashCode() {
    UL ul1 = new UL(TEST_UL_0);
    UL ul2 = new UL(TEST_UL_0);
    UL ul3 = new UL(TEST_UL_GROUP);

    assertEquals(ul1.hashCode(), ul2.hashCode());
    assertNotEquals(ul1.hashCode(), ul3.hashCode());
  }

  @Test
  public void testGetOctet() {
    UL ul = new UL(TEST_UL_0);
    assertEquals((byte) 0x06, ul.getOctet(0));
    assertEquals((byte) 0x05, ul.getOctet(7));
    assertEquals((byte) 0x00, ul.getOctet(15));
    assertThrows(ArrayIndexOutOfBoundsException.class, () -> ul.getOctet(16));
  }

  @Test
  public void testIsClassMethods() {
    byte[] class13bytes = TEST_UL_0.clone();
    class13bytes[8] = 13;
    UL class13UL = new UL(class13bytes);
    assertTrue(class13UL.isClass13());
    assertFalse(class13UL.isClass14());
    assertFalse(class13UL.isClass15());

    byte[] class14bytes = TEST_UL_0.clone();
    class14bytes[8] = 14;
    UL class14UL = new UL(class14bytes);
    assertFalse(class14UL.isClass13());
    assertTrue(class14UL.isClass14());
    assertFalse(class14UL.isClass15());

    byte[] class15bytes = TEST_UL_0.clone();
    class15bytes[8] = 15;
    UL class15UL = new UL(class15bytes);
    assertFalse(class15UL.isClass13());
    assertFalse(class15UL.isClass14());
    assertTrue(class15UL.isClass15());
  }
}