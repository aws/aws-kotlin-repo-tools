/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlannedRemovalRuleTest {
    fun runRegexTestCases(minor: Int, major: Int) {
        val regex = plannedRemovalRegex(major, minor)

        assertTrue(regex.containsMatchIn("@PlannedRemoval($major,$minor)"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval($major,$minor    )"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval($major,    $minor)"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval($major    ,$minor)"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval(    $major,$minor)"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval(    $major    ,    $minor    )"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval(major=$major,minor=$minor)"))
        assertTrue(regex.containsMatchIn("@PlannedRemoval(    major=    $major    ,    minor=    $minor    )"))

        assertFalse(regex.containsMatchIn("@PlannedRemoval"))
        assertFalse(regex.containsMatchIn("@PlannedRemoval()"))
        assertFalse(regex.containsMatchIn("@PlannedRemoval($minor,$minor)"))
        assertFalse(regex.containsMatchIn("@PlannedRemoval($major,$major)"))
        assertFalse(regex.containsMatchIn("@PlannedRemoval($minor,$major)"))
    }

    @Test
    fun testRegex() {
        runRegexTestCases(0, 1)
        runRegexTestCases(1, 70)
        runRegexTestCases(100, 1_000_000)
    }
}
