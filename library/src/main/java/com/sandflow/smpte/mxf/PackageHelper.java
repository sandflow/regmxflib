package com.sandflow.smpte.mxf;

import java.time.LocalDateTime;

import org.apache.commons.numbers.fraction.Fraction;

import com.sandflow.smpte.mxf.types.ComponentStrongReferenceVector;
import com.sandflow.smpte.mxf.types.Package;
import com.sandflow.smpte.mxf.types.Sequence;
import com.sandflow.smpte.mxf.types.SourceClip;
import com.sandflow.smpte.mxf.types.TimelineTrack;
import com.sandflow.smpte.mxf.types.TrackStrongReferenceVector;
import com.sandflow.smpte.util.UMID;
import com.sandflow.smpte.util.UUID;

public class PackageHelper {
  public static void initSingleTrackPackage(Package p, Fraction editRate, Long duration,
      UMID sourcePackageID, Long essenceTrackNum) {
    var sc = new SourceClip();
    sc.InstanceID = UUID.fromRandom();
    sc.ComponentLength = duration == null ? -1L : duration;
    sc.ComponentDataDefinition = Labels.SoundEssenceTrack;
    sc.StartPosition = 0L;
    sc.SourceTrackID = 1L;
    sc.SourcePackageID = sourcePackageID;

    var seq = new Sequence();
    seq.InstanceID = UUID.fromRandom();
    seq.ComponentLength = sc.ComponentLength;
    seq.ComponentDataDefinition = sc.ComponentDataDefinition;
    seq.ComponentObjects = new ComponentStrongReferenceVector();
    seq.ComponentObjects.add(sc);

    var track = new TimelineTrack();
    track.InstanceID = UUID.fromRandom();
    track.TrackID = 1L;
    track.EditRate = editRate;
    track.EssenceTrackNumber = essenceTrackNum != null ? essenceTrackNum : 0L;
    track.Origin = 0L;
    track.TrackSegment = seq;

    p.InstanceID = UUID.fromRandom();
    p.CreationTime = LocalDateTime.now();
    p.PackageID = UMID.fromUUID(p.InstanceID);
    p.PackageLastModified = p.CreationTime;
    p.PackageTracks = new TrackStrongReferenceVector();
    p.PackageTracks.add(track);
  }
}
