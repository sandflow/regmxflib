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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.klv.exceptions.KLVException;
import com.sandflow.smpte.mxf.Labels;
import com.sandflow.smpte.mxf.MXFFiles;
import com.sandflow.smpte.mxf.StreamingWriter.UIDGenerator;
import com.sandflow.smpte.mxf.types.AUIDSet;
import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.ContentStorage;
import com.sandflow.smpte.mxf.types.EssenceData;
import com.sandflow.smpte.mxf.types.EssenceDataStrongReferenceSet;
import com.sandflow.smpte.mxf.types.FileDescriptor;
import com.sandflow.smpte.mxf.types.FileDescriptorStrongReferenceVector;
import com.sandflow.smpte.mxf.types.Identification;
import com.sandflow.smpte.mxf.types.IdentificationStrongReferenceVector;
import com.sandflow.smpte.mxf.types.MaterialPackage;
import com.sandflow.smpte.mxf.types.MultipleDescriptor;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.PackageStrongReferenceSet;
import com.sandflow.smpte.mxf.types.Preface;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.SourcePackage;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.TrackStrongReferenceVector;
import com.sandflow.smpte.mxf.types.Version;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class OP1aHelper {

  public record TrackInfo(byte trackId, UL essenceKey,
      FileDescriptor descriptor,
      AUID dataDefinition, String trackName) {
  }

  public record EssenceContainerInfo(
      java.util.List<TrackInfo> tracks,
      java.util.Set<AUID> conformsToSpecifications,
      Fraction editRate,
      long bodySID,
      long indexSID,
      Long duration) {
  }

  private final EssenceContainerInfo ecInfo;
  private final Preface preface;

  final Map<Byte, UL> trackIDToElementKeys = new HashMap<>();

  public OP1aHelper(EssenceContainerInfo ecInfo, Identification identification, UIDGenerator uidg) throws IOException, KLVException {

    if (ecInfo == null) {
      throw new IllegalArgumentException("Essence info must not be null");
    }
    this.ecInfo = ecInfo;

    if (ecInfo.tracks().size() > 127 || ecInfo.tracks().size() == 0)
      throw new RuntimeException("Number of tracks must be between 1 and 127");
    final byte trackCount = (byte) ecInfo.tracks().size();

    if (ecInfo.bodySID() == 0 || ecInfo.indexSID() == 0)
      throw new RuntimeException("BodySID and IndexSID must be non-zero");

    if (uidg == null) {
      throw new IllegalArgumentException("UID generator must not be null");
    }

    if (identification == null) {
      throw new IllegalArgumentException("Identification must not be null");
    }

    if (identification.FileModificationDate == null) {
      throw new IllegalArgumentException("Identification FileModificationDate must not be null");
    }

    /* File Package */
    SourcePackage sp = new SourcePackage();
    initPackage(sp, uidg, identification.FileModificationDate, "Top-level File Package");

    /* Material Package */
    var mp = new MaterialPackage();
    initPackage(mp, uidg, identification.FileModificationDate, "Material Package");

    /* Create essence tracks */

    Map<UL, Byte> itemCountByKey = new HashMap<>();

    for (byte i = 0; i < trackCount; i++) {
      byte trackId = ecInfo.tracks().get(i).trackId();
      if (trackId < 1 || trackIDToElementKeys.containsKey(trackId)) {
        throw new RuntimeException();
      }

      FileDescriptor d = ecInfo.tracks().get(i).descriptor();
      /**
       * EXCEPTION: some MXF files do not have one essence descriptor per track
       */
      if (d != null) {
        d.EssenceLength = this.ecInfo.duration();
        d.LinkedTrackID = (long) trackId /* ecInfo.tracks().size() > 1 true ? (long) trackId : null */;
      }

      byte itemCount = (byte) (itemCountByKey.getOrDefault(ecInfo.tracks().get(i).essenceKey(), (byte) 0) + 1);
      itemCountByKey.put(ecInfo.tracks().get(i).essenceKey(), itemCount);

      UL elementKey = MXFFiles.makeEssenceElementKey(ecInfo.tracks().get(i).essenceKey(), itemCount, (byte) trackId);

      this.trackIDToElementKeys.put(trackId, elementKey);

      sp.PackageTracks.add(makeTimelineTrack(uidg, ecInfo.editRate(),
          this.ecInfo.duration() == null ? -1L : this.ecInfo.duration(), UMID.NULL_UMID,
          (long) MXFFiles.getTrackNumber(elementKey), null, (long) trackId,
          ecInfo.tracks().get(i).dataDefinition(), ecInfo.tracks().get(i).trackName));

      mp.PackageTracks
          .add(makeTimelineTrack(uidg, ecInfo.editRate(),
              this.ecInfo.duration() == null ? -1L : this.ecInfo.duration(), sp.PackageID, null, (long) trackId,
              (long) trackId,
              ecInfo.tracks().get(i).dataDefinition(), ecInfo.tracks().get(i).trackName));
    }

    List<FileDescriptor> fds = ecInfo.tracks().stream().map(e -> e.descriptor()).filter(e -> e != null).toList();

    if (fds.size() == 1) {
      sp.EssenceDescription = fds.get(0);
    } else {
      MultipleDescriptor md = new MultipleDescriptor();
      md.InstanceID = uidg.generate(md);
      md.EssenceLength = null;
      md.SampleRate = ecInfo.editRate();
      md.ContainerFormat = Labels.MXFGCGenericEssenceMultipleMappings;
      md.FileDescriptors = new FileDescriptorStrongReferenceVector();
      md.FileDescriptors.addAll(fds);
      sp.EssenceDescription = md;
    }

    /* EssenceDataObject */
    var edo = new EssenceData();
    edo.InstanceID = uidg.generate(edo);
    edo.EssenceStreamID = this.ecInfo.bodySID();
    edo.IndexStreamID = this.ecInfo.indexSID();
    edo.LinkedPackageID = sp.PackageID;

    /* Content Storage Object */
    var cs = new ContentStorage();
    cs.InstanceID = uidg.generate(cs);
    cs.Packages = new PackageStrongReferenceSet();
    cs.Packages.add(mp);
    cs.Packages.add(sp);
    cs.EssenceDataObjects = new EssenceDataStrongReferenceSet();
    cs.EssenceDataObjects.add(edo);

    /* EssenceContainers */
    var ecs = new AUIDSet();
    for (TrackInfo info : ecInfo.tracks()) {
      /**
       * EXCEPTION: some descriptor can be null
       */
      if (info.descriptor() != null) {
        AUID ecLabel = info.descriptor().ContainerFormat;
        if (ecLabel != null && !ecs.contains(ecLabel)) {
          ecs.add(ecLabel);
        }
      }
    }

    /* Identification */
    var idList = new IdentificationStrongReferenceVector();
    idList.add(identification.copyOf());

    /* preface */
    this.preface = new Preface();
    this.preface.InstanceID = uidg.generate(this.preface);
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

  public Preface getPreface() {
    return this.preface;
  }

  public UL getElementKey(byte trackId) {
    return this.trackIDToElementKeys.get(trackId);
  }

  static TimelineTrack makeTimelineTrack(UIDGenerator uidg, Fraction editRate, Long duration,
      UMID sourcePackageID, Long essenceTrackNum, Long sourceTrackID, Long trackID, AUID dataDefinition, String trackName) {
    var sc = new SourceClip();
    sc.InstanceID = uidg.generate(sc);
    sc.ComponentLength = duration;
    sc.ComponentDataDefinition = dataDefinition;
    sc.StartPosition = 0L;
    sc.SourceTrackID = sourceTrackID == null ? 0 : sourceTrackID;
    sc.SourcePackageID = sourcePackageID == null ? UMID.NULL_UMID : sourcePackageID;
  
    var seq = new Sequence();
    seq.InstanceID = uidg.generate(seq);
    seq.ComponentLength = sc.ComponentLength;
    seq.ComponentDataDefinition = sc.ComponentDataDefinition;
    seq.ComponentObjects = new ComponentStrongReferenceVector();
    seq.ComponentObjects.add(sc);
  
    var track = new TimelineTrack();
    track.InstanceID = uidg.generate(track);
    if (trackID != null)
      track.TrackID = trackID;
    track.EditRate = editRate;
    track.EssenceTrackNumber = essenceTrackNum != null ? essenceTrackNum : 0L;
    track.Origin = 0L;
    track.TrackSegment = seq;
    track.TrackName = trackName;
  
    return track;
  }

  static void initPackage(Package p, UIDGenerator uidg, LocalDateTime creationTime, String packageName) {
    p.InstanceID = uidg.generate(p);
    p.CreationTime = creationTime;
    p.PackageID = UMID.fromUUID(p.InstanceID);
    p.PackageLastModified = p.CreationTime;
    p.PackageName = packageName;
    p.PackageTracks = new TrackStrongReferenceVector();
  }

}
