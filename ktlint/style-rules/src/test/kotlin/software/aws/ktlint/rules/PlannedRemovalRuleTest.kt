/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class PlannedRemovalRuleTest {
    val ruleEngine = KtLintRuleEngine(
        setOf(
            RuleProvider { PlannedRemovalRule() },
        ),
    )

    private fun hasLintingErrors(codeSnippet: String): Boolean {
        val code = Code.fromSnippet(codeSnippet)
        var hasErrors = false
        ruleEngine.lint(code) {
            // Error callback function
            hasErrors = true
        }
        return hasErrors
    }

    @Test
    fun testRule() {
        assertEquals(
            false,
            hasLintingErrors(
                """
                @PlannedRemoval(1, 2)
                @Deprecated
                class Foo {}
                """.trimIndent(),
            ),
        )

        assertEquals(
            false,
            hasLintingErrors(
                """
                @Deprecated
                @PlannedRemoval(1, 2)
                class Foo {}
                """.trimIndent(),
            ),
        )

        assertEquals(
            false,
            hasLintingErrors(
                """
                @Deprecated
                class Foo {}
                """.trimIndent(),
            ),
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @PlannedRemoval(1, 2)
                class Foo {}
                """.trimIndent(),
            ),
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @PlannedRemoval(1, 2)
                class Foo {}
                
                @Deprecated
                class Bar {}
                """.trimIndent(),
            ),
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @PlannedRemoval(1, 2)
                class Foo {}
                
                @PlannedRemoval(1, 2)
                class Bar {}
                """.trimIndent(),
            ),
        )
    }
}
