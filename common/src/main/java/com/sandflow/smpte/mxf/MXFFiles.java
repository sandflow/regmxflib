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

/**
* @author Pierre-Anthony Lemieux
*/

package com.sandflow.smpte.mxf;

import java.io.IOException;
import java.io.InputStream;

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.PartitionPack.Kind;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;

/**
 * Utilities for processing MXF files
 *
 */
public class MXFFiles {


  /**
   * Creates an Essence Element Key according to SMPTE ST 379-1/ST 379-2
   * 
   * @param essenceKey Base Essence Element Key
   * @param elementCountInItem Count of elements in the item
   * @param elementIDInItem ID of the element in the item
   * @return Essence Element Key
   */
  public static UL makeEssenceElementKey(UL essenceKey, byte elementCountInItem, byte elementIDInItem) {
    byte[] key = essenceKey.getValue().clone();
    key[15] = elementIDInItem;
    key[13] = elementCountInItem;
    return new UL(key);
  }

  /**
   * Extracts the Track Number from an Essence Element Key according to SMPTE ST 379-1/ST 379-2
   * 
   * @param essenceKey Essence Element Key
   * @return Track Number
   */
  public static int getTrackNumber(UL essenceKey) {
    return ((essenceKey.getValueOctet(12) & 0xFF) << 24) +
        ((essenceKey.getValueOctet(13) & 0xFF) << 16) +
        ((essenceKey.getValueOctet(14) & 0xFF) << 8) +
        (essenceKey.getValueOctet(15) & 0xFF);
  }

  /**
   * Information about an essence element
   * 
   * @param key Key of the element
   * @param length Length of the element
   * @param sid BodySID of the element (if applicable, otherwise 0)
   */
  static public record EssenceElementInfo(AUID key, long length, long sid) {
  }

  /**
   * Reads the next essence element from the input stream, skipping over non-essence elements
   * 
   * @param is Input stream
   * @return Information about the next essence element, or null if the end of the stream is reached
   * @throws IOException
   * @throws KLVException
   */
  public static EssenceElementInfo nextElement(InputStream is) throws IOException, KLVException {
    MXFDataInput mis = new MXFDataInput(is);

    long sid = 0;
    AUID elementKey = mis.readAUID();
    long elementLength = mis.readBERLength();

    /* skip over non-GC items */
    while (true) {

      if (elementKey.isUUID() || FillItem.isInstance(elementKey)) {
        /* skip over Fill items and KLVs that do not have a UL key */
        mis.skipFully(elementLength);
        elementKey = mis.readAUID();
        elementLength = mis.readBERLength();
        continue;
      }

      if (!PartitionPack.isInstance(elementKey)) {
        /* we have reached what looks like a GC element */
        break;
      }

      /* found a partition */
      /* partition pack is fixed length so that cast is ok */
      byte[] value = new byte[(int) elementLength];
      mis.readFully(value);
      PartitionPack pp = PartitionPack.fromTriplet(new MemoryTriplet(elementKey, value));

      /* we are done when we reach the footer partition */
      if (pp.getKind() == Kind.FOOTER) {
        return null;
      }

      sid = pp.getBodySID();

      long headerAndIndexBytes = pp.getHeaderByteCount() + pp.getIndexByteCount();

      /*
       * skip the optional fill item and any index and header bytes that follows the
       * partition pack. There is no way to know for sure whether there is a fill item
       * after the partition pack, so we need to make a speculative read.
       */
      elementKey = mis.readAUID();

      if (FillItem.isInstance(elementKey)) {
        elementLength = mis.readBERLength();
        mis.skipFully(elementLength + headerAndIndexBytes);
        elementKey = mis.readAUID();

      } else if (headerAndIndexBytes > 0) {
        mis.skipFully(headerAndIndexBytes - UL.SIZE);
        elementKey = mis.readAUID();
      }

      elementLength = mis.readBERLength();
    }

    return new EssenceElementInfo(elementKey, elementLength, sid);
  }

}
