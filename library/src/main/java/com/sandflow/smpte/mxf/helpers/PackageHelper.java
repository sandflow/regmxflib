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

import java.time.LocalDateTime;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.TrackStrongReferenceVector;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class PackageHelper {
  public static void initPackage(Package p, String packageName) {
    p.InstanceID = UUID.fromRandom();
    p.CreationTime = LocalDateTime.now();
    p.PackageID = UMID.fromUUID(p.InstanceID);
    p.PackageLastModified = p.CreationTime;
    p.PackageName = packageName;
    p.PackageTracks = new TrackStrongReferenceVector();
  }

  public static void initSingleTrackPackage(Package p, Fraction editRate, Long duration,
      UMID sourcePackageID, Long essenceTrackNum, Long sourceTrackID, AUID dataDefinition) {
    PackageHelper.initPackage(p, null);
    p.PackageTracks
        .add(makeTimelineTrack(editRate, duration, sourcePackageID, essenceTrackNum, sourceTrackID, 1L, dataDefinition));
  }

  public static TimelineTrack makeTimelineTrack(Fraction editRate, Long duration,
      UMID sourcePackageID, Long essenceTrackNum, Long sourceTrackID, Long trackID, AUID dataDefinition) {
    var sc = new SourceClip();
    sc.InstanceID = UUID.fromRandom();
    sc.ComponentLength = duration;
    sc.ComponentDataDefinition = dataDefinition;
    sc.StartPosition = 0L;
    sc.SourceTrackID = sourceTrackID == null ? 0 : sourceTrackID;
    sc.SourcePackageID = sourcePackageID == null ? UMID.NULL_UMID : sourcePackageID;

    var seq = new Sequence();
    seq.InstanceID = UUID.fromRandom();
    seq.ComponentLength = sc.ComponentLength;
    seq.ComponentDataDefinition = sc.ComponentDataDefinition;
    seq.ComponentObjects = new ComponentStrongReferenceVector();
    seq.ComponentObjects.add(sc);

    var track = new TimelineTrack();
    track.InstanceID = UUID.fromRandom();
    if (trackID != null)
      track.TrackID = trackID;
    track.EditRate = editRate;
    track.EssenceTrackNumber = essenceTrackNum != null ? essenceTrackNum : 0L;
    track.Origin = 0L;
    track.TrackSegment = seq;

    return track;
  }
}
