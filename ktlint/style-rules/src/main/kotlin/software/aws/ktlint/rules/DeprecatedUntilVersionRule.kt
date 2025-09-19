/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Creates a ktlint rule that forces APIs annotated with @DeprecatedUntilVersion to also be annotated with @Deprecated.
 */
class DeprecatedUntilVersionRule : Rule(RuleId("$RULE_SET:deprecated-until-version"), About()) {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType == ElementType.MODIFIER_LIST) {
            val annotations = node.getChildren(null).filter { it.elementType == ElementType.ANNOTATION_ENTRY }
            val deprecated = annotations.any { it.text.startsWith("@Deprecated") }
            val deprecatedUntilVersion = annotations.any { it.text.startsWith("@DeprecatedUntilVersion") }

            if (deprecatedUntilVersion && !deprecated) {
                emit(
                    node.startOffset,
                    "APIs annotated with @DeprecatedUntilVersion must also be annotated with @Deprecated",
                    false,
                )
            }
        }
    }
}
