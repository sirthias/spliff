spliff
======

_spliff_ is a small, zero-dependency [Scala] library providing efficient implementations of the diff algorithm and
supporting logic presented by _Eugene W. Myers_ in [his 1986 paper "An O(ND) Difference Algorithm and Its Variations".][1]

Myers' algorithm is the default diffing logic for many popular tools (like `git diff`) because it performs well
(time- and memory-wise) and tends to produce diffing results that humans would consider "good".

Many improvements and refinements have been proposed over Myers' original algorithm since 1986.\  
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
  "io.bullet" %% "spliff" % "0.5.0"
)
```


Usage
-----

```scala
import io.bullet.spliff.Diff

// create a 'diff' between two `IndexedSeq[T]`
val diff = Diff("the base sequence", "the target sequence")

// a `Diff` instance gives you several different "views" onto the diffing result

// all delete operations required to get from `base` to `target`
val deletes: Seq[Diff.Op.Delete] = diff.deletes

// all insert operations required to get from `base` to `target`
val insert: Seq[Diff.Op.Insert] = diff.inserts

// all deletes and inserts combined
val delIns: Seq[Diff.Op.DelIns] = diff.delInsOps
val delInsSorted: Seq[Diff.Op.DelIns] = diff.delInsOpsSorted // same but sorted by index

// the diff result as a list of 'delete', 'insert' and 'move' operations
val delInsMov: Seq[Diff.Op.DelInsMov] = diff.delInsMovOps
val delInsMovSorted: Seq[Diff.Op.DelInsMov] = diff.delInsMovOpsSorted // same but sorted by index

// the diff result as a list of 'delete', 'insert', 'move' and replace operations
val allOps: Seq[Diff.Op] = diff.allOps // already sorted by index

// a bidirectional mapping between each individual base and target index, where possible
val bimap: Diff.Bimap = diff.bimap

// the diff represented as a sequence of "chunks", whereby each chunk contains a number of elements
// along with the information, where this chunks comes from (base, target or both)
val chunks: Seq[Diff.Chunk[T]] = diff.chunks
```

Additionally _spliff_ offers these two supporting functions:

```scala
import io.bullet.spliff.Diff

// determines the longest subsequence of elements that is present in both sequences
val lcs: Seq[T] = Diff.longestCommonSubsequence(base, target)

// determines the minimum number of edits required to transform `base` and `target`,
// whereby one "edit" corresponds to deleting or inserting one single element
val distance: Int = Diff.minEditDistance(base, target)
```

For further information and more detailed API documentation just look at the source code,
which is hopefully not hard to read. (Even though the algorithmic part itself is quite dense.)


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
  [1]: http://www.xmailserver.org/diff2.pdf
  [2]: https://www.mozilla.org/en-US/MPL/2.0/
  [3]: http://en.wikipedia.org/wiki/Copyleft
  [4]: https://www.mozilla.org/en-US/MPL/2.0/FAQ/
  [5]: https://blog.robertelder.org/diff-algorithm/