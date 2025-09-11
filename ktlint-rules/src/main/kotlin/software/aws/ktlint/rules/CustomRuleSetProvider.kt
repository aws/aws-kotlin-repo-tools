/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.aws.ktlint.rules

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class CustomRuleSetProvider : RuleSetProviderV3(RuleSetId("aws-kotlin-repo-tools-rules")) {
    override fun getRuleProviders() = setOf(
        RuleProvider { CopyrightHeaderRule() },
        RuleProvider { ExpressionBodyRule() },
        RuleProvider { MultilineIfElseBlockRule() },
    )
}
