
## MXF concepts

### Data model

At the highest level, an MXF file consists of:

- essence containers and generic streams, which each consists of a sequence of KLV packets containing essence or metadata;

- header metadata, which describes the contents of these essence containers and generic streams and contains additional metadata; and

- index tables, which allow temporal offset within these these essence containers and generic streams to be accessed in constant time and in any order.

### Physical structure

An MXF file is divided into partitions:

- two copies of the header metadata is typically stored in an MXF: at the beginning of the file (file header) and at the end of the file (file footer). The latter is assumed to contain the definitive information, once the entire file is has been written.

- each essence container and generic stream is partitioned into one or more partitions on KLV Triplet boundaries. Partitions from different essence containers and generic streams can be interleaved.

- Each partition that contains data from an essence container or generic stream that is indexed is followed by a partition that contains an index table for that partition.

- At the very end of the file, a random index pack (RIP) contains a table of contents of all the partitions contained in the file

### Essence wrapping

#### Frame-wrapping

In the case of frame-wrapping, each access unit of the essence or data stream is
wrapped into its own KLV triplet (called an _element_) and all elements that belong to
the same edit unit are grouped into a logical _content package_.

Index entries point to the first byte of the K of each element.

#### Clip-wrapping

In the case of clip-wrapping, the entire essence stream is wrapped into a single KLV triplet (also called an _element_).

Index entries are relative to the first byte of the V of each element, _with the
exception of IAB Track Files, where they are relative to the K of the IAB Clip
Wrap element._

### Indexing

Indexes come in one of two forms:

- CBE, where all index entries point to elements of the same size in bytes
- VBE, in all other cases
