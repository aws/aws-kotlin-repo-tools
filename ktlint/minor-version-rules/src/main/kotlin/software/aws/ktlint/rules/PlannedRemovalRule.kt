/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.aws.ktlint.rules

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import java.io.File
import java.util.Properties

/**
 * Matches @PlannedRemoval with either named args (major=x, minor=y) or positional args (x, y)
 */
internal fun plannedRemovalRegex(major: Int, minor: Int): Regex = Regex(
    """@PlannedRemoval\s*\(\s*(?:major\s*=\s*$major\s*,\s*minor\s*=\s*$minor\s*|\s*$major\s*,\s*$minor\s*)\s*\)""",
)

/**
 * Creates a ktlint rule that detects APIs annotated with @PlannedRemoval for the upcoming minor version.
 */
class PlannedRemovalRule : Rule(RuleId("$RULE_SET:planned-removal"), About()) {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType == ElementType.ANNOTATION_ENTRY) {
            val gradleProperties = Properties().apply {
                load(File("gradle.properties").inputStream())
            }

            val sdkVersion = gradleProperties.getProperty("sdkVersion").split(".")
            val majorVersion = sdkVersion[0].toInt()
            val minorVersion = sdkVersion[1].toInt()

            val regex = plannedRemovalRegex(majorVersion, minorVersion + 1)
            if (regex.containsMatchIn(node.text)) {
                emit(
                    node.startOffset,
                    "API is scheduled for removal, remove it before releasing the next minor version.",
                    false,
                )
            }
        }
    }
}
