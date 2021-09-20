spliff
======

_spliff_ is a small, zero-dependency [Scala] library providing efficient implementations of the diff algorithm and
supporting logic presented by _Eugene W. Myers_ in [his 1986 paper "An O(ND) Difference Algorithm and Its Variations".][1]

Myers' algorithm is the default diffing logic for many popular tools (like `git diff`) because it performs well
(time- and memory-wise) and tends to produce diffing results that humans would consider "good".

Many improvements and refinements have been proposed over Myers' original algorithm since 1986.    
The implementations provided by _spliff_ are based on the [work by Robert Elder][5].

_spliff_ sports these features:

- no dependencies except for the Scala standard library
- fast (minimal support structures, light on allocations, cheap integer-logic)
- optional detection of moves and replaces
- stacksafe (i.e. heap-based rather than stack-based recursion)
- operates on any data type (not only `String`)
- customizable, type class based equality logic

_spliff_ is available for [Scala] 2.13, [Scala] 3 and [Scala.js].


Installation
------------

The _spliff_ artifacts live on Maven Central and can be tied into your [SBT] project like this:

```scala
libraryDependencies ++= Seq(
  "io.bullet" %% "spliff" % "0.7.1"
)
```


Usage
-----

Since information about the differences between two sequences of arbitrary objects are useful in a very broad range of
application contexts _spliff_ provides several distinct "views" onto the diff data.  
Simply pick the one(s) that are most suited to the task you are trying to solve:


### 1. Basic Operations

On the most basic level _spliff_ uses Myers' algorithm to describe the difference between two sequences `base` and
`target` as a number of `Diff.Op.Delete` and `Diff.Op.Insert` operations. Instances of these types can be regarded
as mere "decorators" on the underlying `base` and `target` sequences as they don't hold any data elements themselves.
They merely describe in terms of index ranges, which data elements must be deleted or inserted in order to transform
`base` into `target`.  
The least common super type of `Diff.Op.Delete` and `Diff.Op.Insert` is the `Diff.Op.DelIns` trait.

Example:

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff(
  "the base sequence",
  "the target sequence"
)

// all delete operations required to get from `base` to `target`
val deletes: Seq[Diff.Op.Delete] = diff.deletes
deletes ==> ArraySeq(
  Delete(4, 1),
  Delete(6, 1)
)

// all insert operations required to get from `base` to `target`
val inserts: Seq[Diff.Op.Insert] = diff.inserts
inserts ==> ArraySeq(
  Insert(5, 4, 1),
  Insert(7, 6, 2),
  Insert(8, 9, 1)
)

// all deletes and inserts combined
val delIns: Seq[Diff.Op.DelIns] = diff.delInsOps
val delInsSorted: Seq[Diff.Op.DelIns] = diff.delInsOpsSorted // same but sorted by index
```


### 2. Higher-Level Operations

On the next higher-level _spliff_ can refine the basic `Diff.Op.DelIns` operations by identifying `Diff.Op.Move` and,
optionally, `Diff.Op.Replace` operations. While this raises the semantic level it doesn't change the fundamental
"decorator-only" character of the diff result.
Without access to both underlying sequences (`base` and `target`) this representation of the diff is likely of limited
value only.

Example:

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff(
  "the base sequence",
  "the sequence base !"
)

// the diff result as a list of 'delete', 'insert' and 'move' operations
val delInsMov: Seq[Diff.Op.DelInsMov] = diff.delInsMovOps
val delInsMovSorted: Seq[Diff.Op.DelInsMov] = diff.delInsMovOpsSorted // same but sorted by index

delInsMov ==> ArraySeq(
  Move(2, 16, 5),
  Insert(17, 17, 2)
)

// the diff result as a list of 'delete', 'insert', 'move' and 'replace' operations
val allOps: Seq[Diff.Op] = diff.allOps // already sorted by index
```

### 3. Patch

Building upon the `Diff.Op.DelInsMov` operations _spliff_ can also represent the diff as a `Diff.Patch[T]`.
A patch holds a compact representation of all data required to reconstruct the target sequence, given only the `base`.
In addition to the information about which data are to be deleted from the `base` and/or moved to other positions
a patch must therefore contain the actual data elements that are to be _inserted_. 

Example:

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff(
  "the base sequence",
  "the sequence base !"
)

// create a batch
val patch = diff.patch

patch ==> Patch(
  baseSize = 17,
  targetSize = 19,
  steps = ArraySeq(
    Delete(4,1),
    Delete(6,1),
    Insert(5, ArraySeq('t')),
    Insert(7, ArraySeq('r', 'g')),
    Insert(8, ArraySeq('t'))
  )
)

// apply the patch to the base sequence
val newTarget = patch.apply("the base sequence")

// yields the original target sequence
newTarget ==> Right("the target sequence")
```


### 4. Chunks

In addition to the above _spliff_ can represent the diff as a sequence of `Diff.Chunk[T]` instances, which partitions
the `base` and `target` into a list of segments, each of which holds the respective data elements as well as meta data
about where the chunk comes from (only the `base`, only the `target`, both sequences or distinct to each sequence).
For some use cases this diff representation is more suitable and directly usable than the more basic operations or
patches.

Example:

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff(
  "the base sequence",
  "the sequence base !"
)

// the diff represented as a sequence of "chunks"
val chunks: Seq[Diff.Chunk[T]] = diff.chunks

chunks ==> Seq(
  Diff.Chunk.InBoth("the "),
  Diff.Chunk.Distinct("base", "target"),
  Diff.Chunk.InBoth(" sequence")
)
```


### 5. Bimap

Sometimes you need a (bidirectional) mapping between the indices of `base` and the indices of `target`.
_spliff_ makes this readily available.

Example:

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff(
  "the base sequence",
  "the sequence base !"
)

// a bidirectional mapping between each individual base and target index, where possible
val bimap: Diff.Bimap = diff.bimap

bimap.baseToTargetIndex(10) ==> Some(12)
bimap.targetToBaseIndex(12) ==> Some(10)
```


### 6. Longest Common Subsequence and Min Edit Distance

Finally _spliff_ offers these two supporting functions:

```scala
import io.bullet.spliff.Diff

val base = "the base sequence"
val target = "the target sequence"

// determines the longest subsequence of elements that is present in both sequences
val lcs: Seq[Char] = Diff.longestCommonSubsequence(base, target)
lcs.mkString ==> "the ae sequence"

// determines the minimum number of edits required to transform `base` into `target`,
// whereby one "edit" corresponds to deleting or inserting one single element
val distance: Int = Diff.minEditDistance(base, target)
distance ==> 6
```

For further information and more detailed API documentation just look at the [source code], which is hopefully not
that hard to read. (Even though the algorithmic core parts themselves are certainly quite dense.)


Why "spliff"?
-------------

The name _spliff_ is a [portmanteau] of the words "split" and "difference" alluding to the core principle of Myers'
algorithm, which divides the problem of finding a suitable diff into two parts, that are then solved separately
and recursively.

There is, of course, no relationship with other, potentially overloaded meanings of the word "spliff".


License
-------

_spliff_ is released under the [MPL 2.0][2], which is a simple and modern weak [copyleft][3] license.

Here is the gist of the terms that are likely most important to you (disclaimer: the following points are not legally
binding, only the license text itself is):

If you'd like to use _spliff_ as a library in your own applications:

- **_spliff_ is safe for use in closed-source applications.**
  The MPL share-alike terms do not apply to applications built on top of or with the help of _spliff_.
   
- **You do not need a commercial license.**
  The MPL applies to _spliff's_ own source code, not your applications.

If you'd like to contribute to _spliff_:

- You do not have to transfer any copyright.

- You do not have to sign a CLA.

- You can be sure that your contribution will always remain available in open-source form and
  will not *become* a closed-source commercial product (even though it might be *used* by such products!)

For more background info on the license please also see the [official MPL 2.0 FAQ][4].

  [Scala]: https://www.scala-lang.org/
  [SBT]: https://www.scala-sbt.org/
  [scalafmt]: https://scalameta.org/scalafmt/
  [Scala.js]: https://www.scala-js.org/
  [source code]: https://github.com/sirthias/spliff/blob/master/src/main/scala/io/bullet/spliff/Diff.scala
  [portmanteau]: https://en.wikipedia.org/wiki/Portmanteau
  [1]: http://www.xmailserver.org/diff2.pdf
  [2]: https://www.mozilla.org/en-US/MPL/2.0/
  [3]: http://en.wikipedia.org/wiki/Copyleft
  [4]: https://www.mozilla.org/en-US/MPL/2.0/FAQ/
  [5]: https://blog.robertelder.org/diff-algorithm/