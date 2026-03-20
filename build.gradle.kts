/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.compose.compiler) apply false
}

tasks.register("fetchDocs") {
    doLast {
        try {
            val url = java.net.URL("https://docs.worldlabs.ai/api")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            println("DOCS_START")
            println(conn.inputStream.bufferedReader().readText().take(6000))
            println("DOCS_END")
        } catch(e: Exception) {
            println("ERROR: " + e.message)
        }
    }
}
