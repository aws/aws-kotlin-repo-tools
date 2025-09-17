/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeprecatedApiRuleTest {
    val ruleEngine = KtLintRuleEngine(
        ruleProviders = setOf(
            RuleProvider { deprecatedApiRule(1, 2) },
        ),
    )

    private fun runAutoCorrectTest(codeSnippet: String, expected: String) {
        val unformattedCode = Code.fromSnippet(codeSnippet)
        val formattedCode = ruleEngine
            .format(unformattedCode) { AutocorrectDecision.ALLOW_AUTOCORRECT }
            .trimStart()
            .trimEnd()

        assertEquals(expected, formattedCode)
    }

    @Test
    fun testAutoCorrect() {
        runAutoCorrectTest(
            """
                @DeprecatedUntilVersion(1, 2)
                class Foo {
                    fun foo() {}
                }
                
                class Bar {
                    fun bar() {}
                }
            """.trimIndent(),
            """
                class Bar {
                    fun bar() {}
                }
            """.trimIndent(),
        )

        runAutoCorrectTest(
            """
                @DeprecatedUntilVersion(1, 2)
                class Foo {
                    fun foo() {}
                }
            """.trimIndent(),
            """
            """.trimIndent(),
        )

        runAutoCorrectTest(
            """
                @DeprecatedUntilVersion(1, 2)
                fun foo() {
                    // foo foo
                }
                
                class Bar {
                    fun bar() {}
                }
            """.trimIndent(),
            """
                class Bar {
                    fun bar() {}
                }
            """.trimIndent(),
        )
    }

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
