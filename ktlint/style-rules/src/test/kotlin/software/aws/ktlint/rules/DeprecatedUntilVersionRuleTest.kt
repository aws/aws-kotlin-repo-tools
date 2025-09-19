package software.aws.ktlint.rules

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class DeprecatedUntilVersionRuleTest {
    val ruleEngine = KtLintRuleEngine(
        setOf(
            RuleProvider { DeprecatedUntilVersionRule() },
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
                @DeprecatedUntilVersion(1, 2)
                @Deprecated
                class Foo {}
            """.trimIndent(),
            )
        )

        assertEquals(
            false,
            hasLintingErrors(
                """
                @Deprecated
                @DeprecatedUntilVersion(1, 2)
                class Foo {}
            """.trimIndent(),
            )
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @DeprecatedUntilVersion(1, 2)
                class Foo {}
            """.trimIndent(),
            )
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @DeprecatedUntilVersion(1, 2)
                class Foo {}
                
                @Deprecated
                class Bar {}
            """.trimIndent(),
            )
        )

        assertEquals(
            true,
            hasLintingErrors(
                """
                @DeprecatedUntilVersion(1, 2)
                class Foo {}
                
                @DeprecatedUntilVersion(1, 2)
                class Bar {}
            """.trimIndent(),
            )
        )
    }
}