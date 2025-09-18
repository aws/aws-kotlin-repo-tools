/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.aws.ktlint.rules

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class MinorVersionRuleSetProvider : RuleSetProviderV3(RuleSetId("minor-version-strategy-rules")) {
    override fun getRuleProviders() = setOf(
        RuleProvider { DeprecatedApiRule() },
    )
}
