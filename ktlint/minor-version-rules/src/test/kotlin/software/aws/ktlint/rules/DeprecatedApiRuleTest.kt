/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeprecatedApiRuleTest {
    fun runRegexTestCases(minor: Int, major: Int) {
        val regex = deprecatedUntilVersionRegex(major, minor)

        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion($major,$minor)"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion($major,$minor    )"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion($major,    $minor)"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion($major    ,$minor)"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion(    $major,$minor)"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion(    $major    ,    $minor    )"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion(major=$major,minor=$minor)"))
        assertTrue(regex.containsMatchIn("@DeprecatedUntilVersion(    major=    $major    ,    minor=    $minor    )"))

        assertFalse(regex.containsMatchIn("@DeprecatedUntilVersion"))
        assertFalse(regex.containsMatchIn("@DeprecatedUntilVersion()"))
        assertFalse(regex.containsMatchIn("@DeprecatedUntilVersion($minor,$minor)"))
        assertFalse(regex.containsMatchIn("@DeprecatedUntilVersion($major,$major)"))
        assertFalse(regex.containsMatchIn("@DeprecatedUntilVersion($minor,$major)"))
    }

    @Test
    fun testRegex() {
        runRegexTestCases(0, 1)
        runRegexTestCases(1, 70)
        runRegexTestCases(100, 1_000_000)
    }
}
