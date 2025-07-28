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

package com.sandflow.smpte.mxf.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.sandflow.smpte.klv.MemoryTriplet;
import com.sandflow.smpte.klv.Set;
import com.sandflow.smpte.klv.Triplet;
import com.sandflow.smpte.mxf.ClassFactory;
import com.sandflow.smpte.mxf.SetItemAdapter;
import com.sandflow.smpte.mxf.MXFInputContext;
import com.sandflow.smpte.mxf.MXFDataInput;
import com.sandflow.smpte.mxf.MXFOutputContext;
import com.sandflow.smpte.mxf.MXFDataOutput;
import com.sandflow.smpte.mxf.MXFEvent;
import com.sandflow.smpte.mxf.MXFException;
import com.sandflow.smpte.util.AUID;
import com.sandflow.smpte.util.UL;
import com.sandflow.smpte.util.UUID;

/**
 * IndexTableSegment
 *
 * - SliceCount : UInt8 [0..1]
 * - DeltaEntryArray : DeltaEntryArray [0..1]
 * - VBEByteCount : UInt64 [0..1]
 * - ForwardIndexDirection : Boolean [0..1]
 * - IndexEditRate : Rational
 * - IndexStreamID : UInt32 [0..1]
 * - IndexDuration : Int64
 * - PositionTableCount : UInt8 [0..1]
 * - InstanceID : UUID [0..1]
 * - IndexStartPosition : Int64
 * - SingleEssenceLocation : Boolean [0..1]
 * - SingleIndexLocation : Boolean [0..1]
 * - ExtStartOffset : UInt64 [0..1]
 * - IndexEntryArray : IndexEntryArray [0..1]
 * - EditUnitByteCount : UInt32 [0..1]
 * - EssenceStreamID : UInt32 [0..1]
 */
public class IndexTableSegment {
  private static final UL KEY = UL.fromURN("urn:smpte:ul:060e2b34.027f0101.0d010201.01100100");
  public static final int ITEM_LENGTH = 16;

  private static final AUID SliceCount_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010104.04040401.01000000");
  private static final AUID DeltaEntryArray_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.04040401.06000000");
  private static final AUID VBEByteCount_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.0101010a.04060205.00000000");
  private static final AUID ForwardIndexDirection_AUID = AUID
      .fromURN("urn:smpte:ul:060e2b34.0101010e.04040502.00000000");
  private static final AUID IndexEditRate_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.05300406.00000000");
  private static final AUID IndexStreamID_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010104.01030405.00000000");
  private static final AUID IndexDuration_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.07020201.01020000");
  private static final AUID PositionTableCount_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.04040401.07000000");
  private static final AUID InstanceID_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010101.01011502.00000000");
  private static final AUID IndexStartPosition_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.07020103.010a0000");
  private static final AUID SingleEssenceLocation_AUID = AUID
      .fromURN("urn:smpte:ul:060e2b34.0101010e.04060206.00000000");
  private static final AUID SingleIndexLocation_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.0101010e.04040501.00000000");
  private static final AUID ExtStartOffset_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.0101010a.04060204.00000000");
  private static final AUID IndexEntryArray_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010105.04040402.05000000");
  private static final AUID EditUnitByteCount_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010104.04060201.00000000");
  private static final AUID EssenceStreamID_AUID = AUID.fromURN("urn:smpte:ul:060e2b34.01010104.01030404.00000000");

  /**
   * Number of sections indexed, per edit unit, minus one
   */
  public Short SliceCount;

  /**
   * Array of values used to identify elements of Essence within an edit unit
   */
  public com.sandflow.smpte.mxf.types.DeltaEntryArray DeltaEntryArray;

  /**
   * The count of bytes of the last essence element in the last Edit Unit indexed
   * by the Index Table Segment
   */
  public Long VBEByteCount;

  /**
   * Specifies whether the Index Table Segments are pointing forward or backward.
   */
  public Boolean ForwardIndexDirection;

  /**
   * Specifies the indexing rate in hertz
   */
  public org.apache.commons.numbers.fraction.Fraction IndexEditRate;

  /**
   * Index table stream ID
   */
  public Long IndexStreamID;

  /**
   * Specifies the duration of an Index table in content units
   */
  public Long IndexDuration;

  /**
   * Number of temporal position offsets indexed, per edit unit, minus one
   */
  public Short PositionTableCount;

  /**
   * Unique ID of an instance
   */
  public com.sandflow.smpte.util.UUID InstanceID;

  /**
   * Specifies the position reletive to start of essence, in edit units, where
   * indexing starts
   */
  public Long IndexStartPosition;

  /**
   * Specifies whether the Essence Containers are in one partition or multiple
   * partitions.
   */
  public Boolean SingleEssenceLocation;

  /**
   * Specifies whether the Index Table Segments are in one partition or multiple
   * partitions.
   */
  public Boolean SingleIndexLocation;

  /**
   * The byte offset to the first essence data in an external Essence file
   */
  public Long ExtStartOffset;

  /**
   * Array of values used to index elements from edit unit to edit unit
   */
  public com.sandflow.smpte.mxf.types.IndexEntryArray IndexEntryArray;

  /**
   * Defines the byte count of each and every Edit Unit of stored Essence indexed
   * by this Index Table Segment
   */
  public Long EditUnitByteCount;

  /**
   * Essence (or its container) stream ID
   */
  public Long EssenceStreamID;

  void readFromSet(Set s, MXFInputContext ctx) throws IOException {

    Triplet t;

    if ((t = s.getItem(SliceCount_AUID)) != null) {
      this.SliceCount = com.sandflow.smpte.mxf.adapters.UInt8Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(DeltaEntryArray_AUID)) != null) {
      this.DeltaEntryArray = com.sandflow.smpte.mxf.types.DeltaEntryArray
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(VBEByteCount_AUID)) != null) {
      this.VBEByteCount = com.sandflow.smpte.mxf.adapters.UInt64Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(ForwardIndexDirection_AUID)) != null) {
      this.ForwardIndexDirection = com.sandflow.smpte.mxf.adapters.BooleanAdapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(IndexEditRate_AUID)) != null) {
      this.IndexEditRate = com.sandflow.smpte.mxf.adapters.RationalAdapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(IndexStreamID_AUID)) != null) {
      this.IndexStreamID = com.sandflow.smpte.mxf.adapters.UInt32Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(IndexDuration_AUID)) != null) {
      this.IndexDuration = com.sandflow.smpte.mxf.adapters.Int64Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(PositionTableCount_AUID)) != null) {
      this.PositionTableCount = com.sandflow.smpte.mxf.adapters.UInt8Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(InstanceID_AUID)) != null) {
      this.InstanceID = com.sandflow.smpte.mxf.adapters.UUIDAdapter.fromStream(new MXFDataInput(t.getValueAsStream()),
          ctx);
    }

    if ((t = s.getItem(IndexStartPosition_AUID)) != null) {
      this.IndexStartPosition = com.sandflow.smpte.mxf.adapters.Int64Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(SingleEssenceLocation_AUID)) != null) {
      this.SingleEssenceLocation = com.sandflow.smpte.mxf.adapters.BooleanAdapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(SingleIndexLocation_AUID)) != null) {
      this.SingleIndexLocation = com.sandflow.smpte.mxf.adapters.BooleanAdapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(ExtStartOffset_AUID)) != null) {
      this.ExtStartOffset = com.sandflow.smpte.mxf.adapters.UInt64Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(IndexEntryArray_AUID)) != null) {
      this.IndexEntryArray = com.sandflow.smpte.mxf.types.IndexEntryArray
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx, this.SliceCount == null ? 0 : this.SliceCount,
              this.PositionTableCount == null ? 0 : this.PositionTableCount);
    }

    if ((t = s.getItem(EditUnitByteCount_AUID)) != null) {
      this.EditUnitByteCount = com.sandflow.smpte.mxf.adapters.UInt32Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

    if ((t = s.getItem(EssenceStreamID_AUID)) != null) {
      this.EssenceStreamID = com.sandflow.smpte.mxf.adapters.UInt32Adapter
          .fromStream(new MXFDataInput(t.getValueAsStream()), ctx);
    }

  }

  public static IndexTableSegment fromSet(Set s, MXFInputContext ctx) throws MXFException, IOException {
    if (s == null) {
      throw new IllegalArgumentException("Cannot read from an empty Set");
    }
    Class<?> clazz = ClassFactory.getClass(s.getKey());
    if (clazz == null) {
      ctx.handleEvent(new MXFEvent(
        MXFEvent.EventCodes.CLASS_NOT_FOUND,
        "Class not found: "+ s.getKey().toString()));
      return null;
    }

    IndexTableSegment obj;
    try {
      obj = (IndexTableSegment) clazz.getConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    obj.readFromSet(s, ctx);
    return obj;
  }

  public static IndexTableSegment fromStream(MXFDataInput is, MXFInputContext ctx) throws IOException, MXFException {
    UUID uuid = is.readUUID();

    var s = ctx.getSet(uuid);
    if (s == null) {
      ctx.handleEvent(new MXFEvent(
              MXFEvent.EventCodes.MISSING_HEADER_SET,
              "Cannot find header metadata set " + uuid));
      return null;
    }

    return IndexTableSegment.fromSet(s, ctx);
  }

  void addItemsToSet(Set s, MXFOutputContext ctx) throws IOException, MXFException {

    SetItemAdapter.toSetItem(this.SliceCount, SliceCount_AUID,
        com.sandflow.smpte.mxf.adapters.UInt8Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.DeltaEntryArray, DeltaEntryArray_AUID,
        com.sandflow.smpte.mxf.types.DeltaEntryArray::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.VBEByteCount, VBEByteCount_AUID,
        com.sandflow.smpte.mxf.adapters.UInt64Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.ForwardIndexDirection, ForwardIndexDirection_AUID,
        com.sandflow.smpte.mxf.adapters.BooleanAdapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.IndexEditRate, IndexEditRate_AUID,
        com.sandflow.smpte.mxf.adapters.RationalAdapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.IndexStreamID, IndexStreamID_AUID,
        com.sandflow.smpte.mxf.adapters.UInt32Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.IndexDuration, IndexDuration_AUID,
        com.sandflow.smpte.mxf.adapters.Int64Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.PositionTableCount, PositionTableCount_AUID,
        com.sandflow.smpte.mxf.adapters.UInt8Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.InstanceID, InstanceID_AUID,
        com.sandflow.smpte.mxf.adapters.UUIDAdapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.IndexStartPosition, IndexStartPosition_AUID,
        com.sandflow.smpte.mxf.adapters.Int64Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.SingleEssenceLocation, SingleEssenceLocation_AUID,
        com.sandflow.smpte.mxf.adapters.BooleanAdapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.SingleIndexLocation, SingleIndexLocation_AUID,
        com.sandflow.smpte.mxf.adapters.BooleanAdapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.ExtStartOffset, ExtStartOffset_AUID,
        com.sandflow.smpte.mxf.adapters.UInt64Adapter::toStream, s, ctx);
    if (this.IndexEntryArray != null) {
      ByteArrayOutputStream ibos = new ByteArrayOutputStream();
      MXFDataOutput imos = new MXFDataOutput(ibos);
      com.sandflow.smpte.mxf.types.IndexEntryArray.toStream(
          this.IndexEntryArray,
          imos,
          ctx,
          this.SliceCount != null ? this.SliceCount : 0,
          this.PositionTableCount != null ? this.PositionTableCount : 0);
      imos.flush();
      s.addItem(new MemoryTriplet(IndexEntryArray_AUID, ibos.toByteArray()));
    }
    SetItemAdapter.toSetItem(this.EditUnitByteCount, EditUnitByteCount_AUID,
        com.sandflow.smpte.mxf.adapters.UInt32Adapter::toStream, s, ctx);
    SetItemAdapter.toSetItem(this.EssenceStreamID, EssenceStreamID_AUID,
        com.sandflow.smpte.mxf.adapters.UInt32Adapter::toStream, s, ctx);
  }

  public void toSet(MXFOutputContext ctx) throws IOException, MXFException {
    Set s = new Set(KEY);
    this.addItemsToSet(s, ctx);
    ctx.putSet(s);
  }

  public static void toStream(IndexTableSegment value, MXFDataOutput mos, MXFOutputContext ctx) throws IOException, MXFException {
    mos.writeUUID(value.InstanceID);
    value.toSet(ctx);
  }

  /**
   * Returns the Set Key
   *
   * @return UL Key
   */
  public static UL getKey() {
    return KEY;
  }

}