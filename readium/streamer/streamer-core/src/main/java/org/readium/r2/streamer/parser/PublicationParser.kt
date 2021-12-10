/*
 * Module: r2-streamer-kotlin
 * Developers: Quentin Gliosca, Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

@file:Suppress("DEPRECATION")

package org.readium.r2.streamer.parser

import org.readium.r2.shared.publication.Publication
import org.readium.r2.streamer.container.Container
import java.io.File

@Deprecated("Use [Streamer] to parse a publication file.")
data class PubBox(var publication: Publication, var container: Container)

@Deprecated("Use [Streamer] to parse a publication file.")
interface PublicationParser {
    fun parse(fileAtPath: String, fallbackTitle: String = File(fileAtPath).name): PubBox?
}
