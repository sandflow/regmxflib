package com.sandflow.smpte.mxf.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class CharacterAdapterUtilitiesTest {
  @Test
  void testReaderToString() throws IOException {
    InputStreamReader isr = new InputStreamReader(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x6C, (byte) 0x6C, (byte) 0x6F }),
        StandardCharsets.US_ASCII);
    assertEquals("hello", CharacterAdapterUtilities.readerToString(isr, true));

    isr = new InputStreamReader(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x6C, (byte) 0x6C, (byte) 0x6F }),
        StandardCharsets.US_ASCII);
    assertEquals("hello", CharacterAdapterUtilities.readerToString(isr, false));
  }

  @Test
  void testFromStreamWithNull() throws IOException {
    InputStreamReader isr = new InputStreamReader(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x00, (byte) 0x6C, (byte) 0x6F }),
        StandardCharsets.US_ASCII);
    assertEquals("he\0lo", CharacterAdapterUtilities.readerToString(isr, false));

    isr = new InputStreamReader(
        new ByteArrayInputStream(new byte[] { (byte) 0x68, (byte) 0x65, (byte) 0x00, (byte) 0x6C, (byte) 0x6F }),
        StandardCharsets.US_ASCII);
    assertEquals("he", CharacterAdapterUtilities.readerToString(isr, true));

  }
}
