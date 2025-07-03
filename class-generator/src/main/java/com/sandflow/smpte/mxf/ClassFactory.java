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

import java.util.HashMap;

import com.sandflow.smpte.mxf.types.IndexTableSegment;
import com.sandflow.smpte.util.UL;

public class ClassFactory {
  static protected final HashMap<UL, Class<?>> classMap = new HashMap<>();

  static {
    try {
      Class.forName("com.sandflow.smpte.mxf.ClassFactoryInitializer");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ClassFactory.putClass(IndexTableSegment.getKey(), IndexTableSegment.class);
  }

  private ClassFactory() {
  }

  public static Class<?> getClass(UL ul) {
    return classMap.get(makeNormalizedUL(ul));
  }

  protected static void putClass(UL ul, Class<?> clazz) {
    classMap.put(makeNormalizedUL(ul), clazz);
  }

  private static UL makeNormalizedUL(UL ul) {
    byte[] value = ul.getValue().clone();
    /* set version to 0 */
    value[7] = 0;
    if (ul.isGroup()) {
      /* set byte 6 to 0x7f */
      value[5] = 0x7f;
    }
    return new UL(value);
  }
}
