/*
 * Copyright (c) 2014, Pierre-Anthony Lemieux (pal@sandflow.com)
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

package com.sandflow.smpte.mxf.adapters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.mxf.ClassFactory;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFInputStream;
import com.sandflow.smpte.mxf.Set;
import com.sandflow.smpte.mxf.annotation.MXFPropertyDefinition;
import com.sandflow.smpte.util.AUID;

/* TODO: this should be split into StrongReference and ClassLoader */
public class ClassAdapter {

  public static <T> T fromStream(MXFInputStream is, MXFInputContext ctx) {
    try {
      var uuid = is.readUUID();
      var s = ctx.getSet(uuid);
      if (s == null)
        return null;

      return (T) fromSet(s, ctx);
    } catch (Exception e) {
    }

    return null;
  }

  public static Object fromSet(Set s, MXFInputContext ctx) throws IOException, NoSuchMethodException, SecurityException,
      IllegalArgumentException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<?> clazz = ClassFactory.getClass(new AUID(s.getKey()));

    Object obj = clazz.getDeclaredConstructor().newInstance();

    for (Field field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(MXFPropertyDefinition.class)) {
        MXFPropertyDefinition annotation = field.getAnnotation(MXFPropertyDefinition.class);
        field.setAccessible(true);

        Triplet t = s.getItem(AUID.fromURN(annotation.Identification()));
        MXFInputStream is = new MXFInputStream(new ByteArrayInputStream(t.getValue()));

        var method = annotation.AdapterClass().getMethod("fromStream", MXFInputStream.class, MXFInputContext.class);
        field.set(obj, method.invoke(null, is, ctx));
      }
    }

    return obj;
  }

}
