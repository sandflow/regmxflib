package com.sandflow.smpte.mxf;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;

import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

public class Preface {
  private static final UL CONTENTSTORAGE_KEY = UL.fromURN("urn:smpte:ul:060e2b34.01010102.06010104.02010000");

  ContentStorage contentStorage;

  public static Preface fromSet(Set s, HashMap<UUID, Set> resolver) throws EOFException, KLVException, IOException {
    Preface p = new Preface();

    for (Triplet i : s.getItems()) {
      if (CONTENTSTORAGE_KEY.equalsIgnoreVersion(i.getKey())) {
        MXFInputStream kis = new MXFInputStream(i.getValueAsStream());
        p.contentStorage = ContentStorage.fromSet(resolver.get(kis.readUUID()), resolver);
      }
    }

    return p;
  }
}
