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

package com.sandflow.smpte.mxf.helpers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.LocalTagRegister;
import com.sandflow.smpte.klv.LocalTagResolver;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.FillItem;
import com.sandflow.smpte.mxf.Labels;
import com.sandflow.smpte.mxf.MXFFiles;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFOutputStream;
import com.sandflow.smpte.mxf.PartitionPack;
import com.sandflow.smpte.mxf.PrimerPack;
import com.sandflow.smpte.mxf.StaticLocalTags;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.FileDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class OP1aHelper {

  public record TrackInfo(UL essenceKey,
      FileDescriptor descriptor,
      AUID dataDefinition) {
  }

  public record EssenceContainerInfo(
      java.util.List<TrackInfo> tracks,
      java.util.Set<AUID> conformsToSpecifications,
      Fraction editRate) {
  }

  private final EssenceContainerInfo ecInfo;
  private final Preface preface;

  private final long bodySID = 1;
  private final long indexSID = 127;

  final List<UL> elementKeys;

  public OP1aHelper(EssenceContainerInfo ecInfo) throws IOException, KLVException {
    if (ecInfo == null) {
      throw new IllegalArgumentException("Essence info must not be null");
    }
    this.ecInfo = ecInfo;

    if (ecInfo.tracks().size() > 127 || ecInfo.tracks().size() == 0)
      throw new RuntimeException();

    final byte trackCount = (byte) ecInfo.tracks().size();

    /* elementKeys */
    this.elementKeys = new ArrayList<>(trackCount);

    /* File Package */
    SourcePackage sp = new SourcePackage();
    PackageHelper.initPackage(sp, "Top-level File Package");

    /* Material Package */
    var mp = new MaterialPackage();
    PackageHelper.initPackage(mp, "Material Package");

    for (byte i = 0; i < trackCount; i++) {
      FileDescriptor d = ecInfo.tracks().get(i).descriptor();
      d.EssenceLength = 0L;
      d.LinkedTrackID = ecInfo.tracks().size() > 1 ? (long) i : null;

      UL elementKey = MXFFiles.makeEssenceElementKey(ecInfo.tracks().get(i).essenceKey(), trackCount, i);

      this.elementKeys.add(elementKey);

      sp.PackageTracks.add(PackageHelper.makeTimelineTrack(ecInfo.editRate(), null, UMID.NULL_UMID,
          (long) MXFFiles.getTrackNumber(elementKey), null, (long) i,
          ecInfo.tracks().get(i).dataDefinition()));

      mp.PackageTracks
          .add(PackageHelper.makeTimelineTrack(ecInfo.editRate(), /* 24L */ null, sp.PackageID, null, (long) i, null,
              ecInfo.tracks().get(i).dataDefinition()));
    }

    if (trackCount == 1) {
      sp.EssenceDescription = ecInfo.tracks().get(0).descriptor();
    } else {
      MultipleDescriptor md = new MultipleDescriptor();
      md.InstanceID = UUID.fromRandom();
      md.EssenceLength = 0L;
      md.SampleRate = ecInfo.editRate();
      md.ContainerFormat = Labels.MXFGCGenericEssenceMultipleMappings;
      md.FileDescriptors = new FileDescriptorStrongReferenceVector();
      md.FileDescriptors.addAll(ecInfo.tracks().stream().map(e -> e.descriptor()).toList());
      sp.EssenceDescription = md;
    }

    /* TODO: return better error when InstanceID is null */
    /* EssenceDataObject */
    var edo = new EssenceData();
    edo.InstanceID = UUID.fromRandom();
    edo.EssenceStreamID = this.bodySID;
    edo.IndexStreamID = this.indexSID;
    edo.LinkedPackageID = sp.PackageID;

    /* Content Storage Object */
    var cs = new ContentStorage();
    cs.InstanceID = UUID.fromRandom();
    cs.Packages = new PackageStrongReferenceSet();
    cs.Packages.add(mp);
    cs.Packages.add(sp);
    cs.EssenceDataObjects = new EssenceDataStrongReferenceSet();
    cs.EssenceDataObjects.add(edo);

    /* EssenceContainers */
    var ecs = new AUIDSet();
    for (TrackInfo info : ecInfo.tracks()) {
      AUID ecLabel = info.descriptor().ContainerFormat;
      if (!ecs.contains(ecLabel)) {
        ecs.add(ecLabel);
      }
    }

    /* Identification */
    var idList = new IdentificationStrongReferenceVector();
    idList.add(IdentificationHelper.makeIdentification());

    /* preface */
    this.preface = new Preface();
    this.preface.InstanceID = UUID.fromRandom();
    this.preface.FormatVersion = new Version(1, 3);
    this.preface.ObjectModelVersion = 1L;
    this.preface.PrimaryPackage = sp.PackageID;
    this.preface.FileLastModified = LocalDateTime.now();
    this.preface.EssenceContainers = ecs;
    this.preface.IsRIPPresent = true;
    this.preface.OperationalPattern = trackCount > 1 ? Labels.MXFOP1aSingleItemSinglePackageMultiTrackStreamInternal
        : Labels.MXFOP1aSingleItemSinglePackageUniTrackStreamInternal;
    this.preface.IdentificationList = idList;
    this.preface.ContentStorageObject = cs;
    if (this.ecInfo.conformsToSpecifications != null) {
      this.preface.ConformsToSpecifications = new AUIDSet();
      this.preface.ConformsToSpecifications.addAll(this.ecInfo.conformsToSpecifications);
    }
  }

  public Preface getHeaderMetadata() {
    return this.preface;
  }

}
