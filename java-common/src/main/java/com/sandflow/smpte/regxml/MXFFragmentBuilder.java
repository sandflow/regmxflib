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

package com.sandflow.smpte.regxml;

import com.sandflow.smpte.klv.Group;
import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.FillItem;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFEvent;
import com.sandflow.smpte.mxf.MXFException;
import com.sandflow.smpte.mxf.PartitionPack;
import com.sandflow.smpte.mxf.PrimerPack;
import com.sandflow.smpte.regxml.dict.DefinitionResolver;
import com.sandflow.smpte.regxml.dict.definitions.ClassDefinition;
import com.sandflow.smpte.regxml.dict.definitions.Definition;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;
import com.sandflow.util.events.EventHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

/**
 * Builds a RegXML Fragment (SMPTE ST 2001-1) from an MXF file (SMPTE ST 377-1).
 */
public class MXFFragmentBuilder {

  private static final UL INDEX_TABLE_SEGMENT_UL = UL.fromURN("urn:smpte:ul:060e2b34.02530101.0d010201.01100100");

  private static final UL PREFACE_KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010101.01012f00");

  private static final UL INSTANCE_UID_ITEM_UL = UL.fromURN("urn:smpte:ul:060e2b34.01010101.01011502.00000000");

  /**
   * Returns the Instance ID (InstanceUID property) of a Group, or null if the
   * Group does not carry one.
   */
  private static UUID getInstanceID(Group group) {
    for (Triplet t : group.getItems()) {
      if (INSTANCE_UID_ITEM_UL.equalsIgnoreVersion(t.getKey())) {
        return new UUID(t.getValue());
      }
    }
    return null;
  }

  /**
   * Returns a DOM Document Fragment containing a RegXML Fragment rooted at
   * the first Header Metadata object with a class that descends from the
   * specified class.
   *
   * @param mxfpartition     MXF partition, including the Partition Pack. Must
   *                         not be null.
   * @param defresolver      MetaDictionary definitions. Must not be null.
   * @param enumnameresolver Allows the local name of extendible enumeration
   *                         values to be inserted as comments. May be null.
   * @param evthandler       Calls back the caller when an event occurs. May be
   *                         null.
   * @param rootclasskey     Root class of Fragment. The Preface class is used
   *                         if null.
   * @param document         DOM for which the Document Fragment is created.
   *                         Must not be null.
   *
   * @return Document Fragment containing a single RegXML Fragment
   *
   * @throws IOException
   * @throws KLVException
   * @throws MXFException
   * @throws FragmentBuilder.RuleException
   */
  public static DocumentFragment fromInputStream(
      InputStream mxfpartition,
      DefinitionResolver defresolver,
      FragmentBuilder.AUIDNameResolver enumnameresolver,
      EventHandler evthandler,
      UL rootclasskey,
      Document document) throws IOException, KLVException, MXFException, FragmentBuilder.RuleException {

    /* look for the partition pack */
    MXFDataInput kis = new MXFDataInput(mxfpartition);

    PartitionPack pp = null;

    for (Triplet t; (t = kis.readTriplet()) != null;) {

      if ((pp = PartitionPack.fromTriplet(t)) != null) {
        break;
      }
    }

    if (pp == null) {

      MXFException.handle(
          evthandler,
          new MXFEvent(MXFEvent.EventCodes.MISSING_PARTITION_PACK, "No Partition Pack found"));

    }

    /* start counting header metadata bytes */
    kis.resetCount();

    /* look for the primer pack */
    LocalTagRegister localreg = null;

    for (Triplet t; (t = kis.readTriplet()) != null; kis.resetCount()) {

      /* skip fill items, if any */
      if (!FillItem.getKey().equalsIgnoreVersion(t.getKey())) {
        localreg = PrimerPack.createLocalTagRegister(t);
        break;
      }

    }

    if (localreg == null) {

      MXFException.handle(
          evthandler,
          new MXFEvent(MXFEvent.EventCodes.MISSING_PRIMER_PACK, "No Primer Pack found"));
    }

    /* capture all local sets within the header metadata */
    ArrayList<Group> gs = new ArrayList<>();
    HashMap<UUID, Group> setresolver = new HashMap<>();

    for (Triplet t;
        kis.getReadCount() < pp.getHeaderByteCount()
            && (t = kis.readTriplet()) != null;) {

      if (INDEX_TABLE_SEGMENT_UL.equalsIgnoreVersion(t.getKey())) {

        /* stop if Index Table reached */
        MXFException.handle(
            evthandler,
            new MXFEvent(
                MXFEvent.EventCodes.UNEXPECTED_STRUCTURE,
                "Index Table Segment encountered before Header Byte Count bytes read"));

        break;

      } else if (FillItem.getKey().equalsIgnoreVersion(t.getKey())) {

        /* skip fill items */
        continue;
      }

      try {
        Group g = Set.fromLocalSet(t, localreg);

        if (g != null) {

          gs.add(g);

          UUID instanceID = getInstanceID(g);

          if (instanceID != null) {
            setresolver.put(instanceID, g);
          }

        } else {

          MXFException.handle(
              evthandler,
              new MXFEvent(
                  MXFEvent.EventCodes.GROUP_READ_FAILED,
                  String.format("Failed to read Group: %s", t.getKey().toString())));

        }
      } catch (KLVException ke) {

        MXFException.handle(
            evthandler,
            new MXFEvent(
                MXFEvent.EventCodes.GROUP_READ_FAILED,
                String.format("Failed to read Group %s with error %s", t.getKey().toString(), ke.getMessage())));

      }
    }

    for (Group agroup : gs) {

      /*
       * in MXF, the first header metadata set should be the Preface set
       * according to ST 377-1 Section 9.5.1, preceded by Class 14 groups
       */
      if (agroup.getKey().equalsWithMask(PREFACE_KEY, 0b1111101011111111 /* ignore version and Group coding */)) {

        break;

      } else if (!agroup.getKey().isClass14()) {

        MXFException.handle(
            evthandler,
            new MXFEvent(
                MXFEvent.EventCodes.UNEXPECTED_STRUCTURE,
                String.format(
                    "At least one non-class 14 Set %s was found between"
                        + " the Primer Pack and the Preface Set.",
                    agroup.getKey())));

        break;

      }

    }

    /* create the fragment */
    FragmentBuilder fb = new FragmentBuilder(defresolver, setresolver, enumnameresolver, evthandler);

    Group rootgroup = null;

    if (rootclasskey != null) {

      Iterator<Group> iter = gs.iterator();

      /* find first essence descriptor */
      while (rootgroup == null && iter.hasNext()) {

        Group g = iter.next();

        AUID gid = new AUID(g.getKey());

        /* go up the class hierarchy */
        while (rootgroup == null && gid != null) {

          Definition def = defresolver.getDefinition(gid);

          /* skip if not a class instance */
          if (!(def instanceof ClassDefinition)) {
            break;
          }

          /* is it an instance of the requested root object */
          UL gul = def.getIdentification().asUL();

          if (gul.equalsWithMask(rootclasskey, 0b1111101011111111 /* ignore version and Group coding */)) {
            rootgroup = g;

          } else {
            /* get parent class */
            gid = ((ClassDefinition) def).getParentClass();
          }
        }

      }

    } else {

      rootgroup = gs.get(0);

    }

    if (rootgroup == null) {

      MXFException.handle(
          evthandler,
          new MXFEvent(MXFEvent.EventCodes.MISSING_ROOT_OBJECT, "No Root Object found"));

    }

    return fb.fromTriplet(rootgroup, document);

  }

}
