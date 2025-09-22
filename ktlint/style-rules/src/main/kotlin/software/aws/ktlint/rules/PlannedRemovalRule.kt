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
 * Creates a ktlint rule that forces APIs annotated with @PlannedRemoval to also be annotated with @Deprecated.
 */
class PlannedRemovalRule : Rule(RuleId("$RULE_SET:planned-removal"), About()) {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType == ElementType.MODIFIER_LIST) {
            val annotations = node.getChildren(null).filter { it.elementType == ElementType.ANNOTATION_ENTRY }
            val deprecated = annotations.any { it.text.startsWith("@Deprecated") }
            val plannedRemoval = annotations.any { it.text.startsWith("@PlannedRemoval") }

            if (plannedRemoval && !deprecated) {
                emit(
                    node.startOffset,
                    "APIs annotated with @PlannedRemoval must also be annotated with @Deprecated",
                    false,
                )
            }
        }
    }
}
