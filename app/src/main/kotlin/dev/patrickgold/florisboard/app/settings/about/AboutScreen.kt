/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 * Copyright (C) 2026 Marko Bosko, Mantra Productions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.about

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.search.settingsSearchAnchor
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.FlorisCanvasIcon
import org.florisboard.lib.compose.stringRes

/**
 * About, rebuilt for Mantra Voice Type.
 *
 * The header carries Marko Bosko's own name and build number rather than the upstream author's, and
 * the row list is trimmed to what this build actually has: the repository, the release list, the two
 * projects it stands on, and the licence screens.
 *
 * The two credit rows are not decoration. This app is Apache-2.0, inherited from FlorisBoard through
 * Dictate, and neither the licence headers in the source nor ATTRIBUTION.md and NOTICE may be removed.
 * Keeping one visible link per upstream project is both the honest thing and the legal minimum.
 */
@Composable
fun AboutScreen() = FlorisScreen {
    title = stringRes(R.string.about__title)

    val navController = LocalNavController.current
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()

    // Account suffix (a) marks which machine last built this, matching the convention across the
    // rest of Marko's apps. The build number is what the GitHub release is named after.
    // The build number is now the CI run number, passed in at build time, so this is the number
    // printed on the release that was actually downloaded. It used to be a hand-bumped version code
    // that had drifted to 127 while releases were named build 57, meaning About confidently reported
    // a build nobody had ever installed.
    val appVersion = "${BuildConfig.VERSION_NAME} (a), build ${BuildConfig.VERSION_CODE}"

    content {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 32.dp)
        ) {
            FlorisCanvasIcon(
                modifier = Modifier.requiredSize(64.dp),
                iconId = R.mipmap.app_icon,
                contentDescription = stringRes(R.string.app_name_full),
            )
            Text(
                text = stringRes(R.string.app_name_full),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringRes(R.string.about__made_by),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Preference(
            icon = Icons.Outlined.Info,
            modifier = Modifier.settingsSearchAnchor("about__version__title"),
            title = stringRes(R.string.about__version__title),
            summary = appVersion,
            onClick = {
                try {
                    clipboardManager.addNewPlaintext(appVersion)
                    Toast.makeText(context, R.string.about__version_copied__title, Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(
                        context,
                        context.stringRes(R.string.about__version_copied__error, "error_message" to e.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
        Preference(
            icon = Icons.Default.Code,
            modifier = Modifier.settingsSearchAnchor("about__repository__title"),
            title = stringRes(R.string.about__repository__title),
            summary = stringRes(R.string.about__repository__summary),
            onClick = { context.launchUrl(R.string.florisboard__repo_url) },
        )
        Preference(
            icon = Icons.Default.Download,
            modifier = Modifier.settingsSearchAnchor("about__releases__title"),
            title = stringRes(R.string.about__releases__title),
            summary = stringRes(R.string.about__releases__summary),
            onClick = { context.launchUrl(R.string.florisboard__changelog_url) },
        )
        Preference(
            icon = Icons.Default.CallSplit,
            modifier = Modifier.settingsSearchAnchor("about__based_on_floris__title"),
            title = stringRes(R.string.about__based_on_floris__title),
            summary = stringRes(R.string.about__based_on_floris__summary),
            onClick = { context.launchUrl(R.string.florisboard__upstream_repo_url) },
        )
        Preference(
            icon = Icons.Default.Keyboard,
            modifier = Modifier.settingsSearchAnchor("about__floris__title"),
            title = stringRes(R.string.about__floris__title),
            summary = stringRes(R.string.about__floris__summary),
            onClick = { context.launchUrl(R.string.florisboard__floris_repo_url) },
        )
        Preference(
            icon = Icons.Outlined.Policy,
            modifier = Modifier.settingsSearchAnchor("about__privacy_policy__title"),
            title = stringRes(R.string.about__privacy_policy__title),
            summary = stringRes(R.string.about__privacy_policy__summary),
            onClick = { context.launchUrl(R.string.florisboard__privacy_policy_url) },
        )
        Preference(
            icon = Icons.Outlined.Description,
            modifier = Modifier.settingsSearchAnchor("about__project_license__title"),
            title = stringRes(R.string.about__project_license__title),
            summary = stringRes(R.string.about__project_license__summary, "license_name" to "Apache 2.0"),
            onClick = { navController.navigate(Routes.Settings.ProjectLicense) },
        )
        Preference(
            icon = Icons.Outlined.Description,
            title = stringRes(id = R.string.about__third_party_licenses__title),
            summary = stringRes(id = R.string.about__third_party_licenses__summary),
            onClick = { navController.navigate(Routes.Settings.ThirdPartyLicenses) },
        )
        Preference(
            icon = Icons.Outlined.Description,
            modifier = Modifier.settingsSearchAnchor("about__data_attributions__title"),
            title = stringRes(id = R.string.about__data_attributions__title),
            summary = stringRes(id = R.string.about__data_attributions__summary),
            onClick = { navController.navigate(Routes.Settings.DataAttributions) },
        )
    }
}
