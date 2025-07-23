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

import com.sandflow.smpte.util.UL;

public class GenericStreamDataElementKey {

  private static UL NODE = UL.fromURN("urn:smpte:ul:060e2b34.0401010a.0d010501.01000000");

  private static int DATA_KEY_DATA_OCTET = 11;
  private static int DATA_KEY_WRAPPING_OCTET = 12;

  public enum KLVType {
    INTRINSIC,
    WRAPPED;
  }

  public enum ByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN,
    UNKNOWN;
  }

  public enum AccessUnitWrapping {
    YES,
    NO;
  }

  public enum MultiKLVWrapping {
    YES,
    NO;
  }

  public enum EssenceSync {
    FRAME,
    OTHER;
  }

  public static UL make(KLVType type, ByteOrder bo, AccessUnitWrapping au, MultiKLVWrapping multi,
      EssenceSync es) {
    byte[] octets = NODE.getValue().clone();

    byte dataOctet = 0x01;

    switch (type) {
      case INTRINSIC:
        dataOctet |= 0b10;
      case WRAPPED:
        break;
      default:
        throw new RuntimeException();
    }

    switch (bo) {
      case BIG_ENDIAN:
        dataOctet |= 0b1000;
        break;
      case LITTLE_ENDIAN:
        dataOctet |= 0b0100;
        break;
      case UNKNOWN:
        dataOctet |= 0b1100;
        break;
      default:
        throw new RuntimeException();
    }

    octets[DATA_KEY_DATA_OCTET] = dataOctet;

    byte wrappingOctet = 0x01;

    switch (au) {
      case NO:
        break;
      case YES:
        wrappingOctet |= 0b10;
        break;
      default:
        throw new RuntimeException();
    }

    switch (multi) {
      case NO:
        break;
      case YES:
        wrappingOctet |= 0b100;
        break;
      default:
        throw new RuntimeException();
    }

    switch (es) {
      case FRAME:
        wrappingOctet |= 0b1000;
        break;
      case OTHER:
        break;
      default:
        throw new RuntimeException();
    }

    octets[DATA_KEY_WRAPPING_OCTET] = wrappingOctet;

    return new UL(octets);
  }
}
