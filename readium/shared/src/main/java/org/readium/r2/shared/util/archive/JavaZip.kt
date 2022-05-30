/*
 * Module: r2-shared-kotlin
 * Developers: Quentin Gliosca
 *
 * Copyright (c) 2020. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.shared.util.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.extensions.readFully
import org.readium.r2.shared.extensions.readRange
import org.readium.r2.shared.util.io.CountingInputStream
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class JavaZip(private val archive: ZipFile) : Archive {

    private inner class Entry(private val entry: ZipEntry) : Archive.Entry {
        override val path: String get() = entry.name

        override val length: Long? get() = entry.size.takeUnless { it == -1L }

        override val compressedLength: Long?
            get() =
                if (entry.method == ZipEntry.STORED || entry.method == -1)
                    null
                else
                    entry.compressedSize.takeUnless { it == -1L }

        /** [CountingInputStream] is not thread-safe. */
        private val mutex = Mutex()

        override suspend fun read(range: LongRange?): ByteArray = mutex.withLock {
            if (range == null)
                readFully()
            else
                readRange(range)
        }

        private suspend fun readFully(): ByteArray = withContext(Dispatchers.IO) {
            archive.getInputStream(entry).use {
                it.readFully()
            }
        }

        private suspend fun readRange(range: LongRange): ByteArray =
            stream(range.first).readRange(range)

        /**
         * Reading an entry in chunks (e.g. from the HTTP server) can be really slow if the entry
         * is deflated in the archive, because we can't jump to an arbitrary offset in a deflated
         * stream. This means that we need to read from the start of the entry for each chunk.
         *
         * To alleviate this issue, we cache a stream which will be reused as long as the chunks are
         * requested in order.
         *
         * See this issue for more info: https://github.com/readium/r2-shared-kotlin/issues/129
         */
        private suspend fun stream(fromIndex: Long): CountingInputStream {
            // Reuse the current stream if it didn't exceed the requested index.
            stream
                ?.takeIf { it.count <= fromIndex }
                ?.let { return it }

            return withContext(Dispatchers.IO) {
                val newStream = CountingInputStream(archive.getInputStream(entry))
                stream?.close()
                stream = newStream
                newStream
            }
        }

        private var stream: CountingInputStream? = null

        override suspend fun close() {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    stream?.close()
                }
            }
        }

    }

    override suspend fun entries(): List<Archive.Entry> =
        archive.entries().toList().filterNot { it.isDirectory }.mapNotNull { Entry(it) }

    override suspend fun entry(path: String): Archive.Entry {
        val entry = archive.getEntry(path)
            ?: throw Exception("No file entry at path $path.")

        return Entry(entry)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        archive.close()
    }

}

internal class JavaZipArchiveFactory : ArchiveFactory {

    override suspend fun open(file: File, password: String?): Archive = withContext(Dispatchers.IO) {
        JavaZip(ZipFile(file))
    }

}
