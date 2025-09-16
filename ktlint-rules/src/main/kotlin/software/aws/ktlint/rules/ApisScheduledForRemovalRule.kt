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
 * Matches @DeprecatedUntilVersion with either named args (major=x, minor=y) or positional args (x, y)
 */
internal fun deprecatedUntilVersionRegex(major: Int, minor: Int): Regex =
    Regex(
        """@DeprecatedUntilVersion\s*\(\s*(?:major\s*=\s*$major\s*,\s*minor\s*=\s*$minor\s*|\s*$major\s*,\s*$minor\s*)\s*\)""",
    )

/**
 * Creates a ktlint rule that detects APIs annotated with @DeprecatedUntilVersion for the specified versions.
 * If autocorrect is enabled, the API will be deleted.
 */
fun apisScheduledForRemovalRule(major: Int, minor: Int): Rule =
    object : Rule(RuleId("minor-version-strategy:apis-scheduled-for-removal"), About()) {
        override fun beforeVisitChildNodes(
            node: ASTNode,
            autoCorrect: Boolean,
            emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
        ) {
            if (node.elementType == ElementType.ANNOTATION_ENTRY) {
                val regex = deprecatedUntilVersionRegex(major, minor)
                if (regex.containsMatchIn(node.text)) {
                    emit(
                        node.startOffset,
                        "The deprecated API is scheduled for removal, please remove it before releasing the next minor version.",
                        true,
                    )

                    if (autoCorrect) {
                        node.treeParent.treeParent?.let {
                            it.treeParent.removeChild(it)
                        }
                    }
                }
            }
        }
    }
