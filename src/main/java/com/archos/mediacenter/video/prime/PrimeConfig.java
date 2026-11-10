// Copyright 2026 tasin-py (NovaPlayer Prime)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.prime;

/**
 * NovaPlayer Prime: single place for fork-specific settings.
 *
 * NovaPlayer Prime is a fork of NOVA Video Player (https://github.com/nova-video-player),
 * itself based on the Archos Video Player Community Edition. Forked and customized by tasin-py.
 */
public final class PrimeConfig {

    private PrimeConfig() {}

    /** GitHub account owning the fork. */
    public static final String GITHUB_OWNER = "tasin-py";
    /** Repository whose GitHub Releases provide app updates. */
    public static final String GITHUB_REPO = "NovaPlayer-Plus";

    public static final String GITHUB_PROFILE_URL = "https://github.com/" + GITHUB_OWNER;
    public static final String GITHUB_REPO_URL = GITHUB_PROFILE_URL + "/" + GITHUB_REPO;
    /**
     * Normal web links, not the GitHub API: the API allows only 60 requests per hour per IP,
     * which ISPs that share one public IP between many customers use up quickly.
     * "releases/latest" redirects to ".../releases/tag/&lt;tag&gt;".
     */
    public static final String LATEST_RELEASE_PAGE = GITHUB_REPO_URL + "/releases/latest";
    public static final String RELEASE_TAG_PAGE = GITHUB_REPO_URL + "/releases/tag/";
    public static final String RELEASE_DOWNLOAD = GITHUB_REPO_URL + "/releases/download/";
    /** Each release also carries "prime-&lt;abi&gt;.apk" so the file name is predictable. */
    public static final String APK_NAME_PREFIX = "prime-";

    /** Release tags look like "prime-2675123": the number is the APK versionCode. */
    public static final String RELEASE_TAG_PREFIX = "prime-";

    /** "Buy me a coffee" page. */
    public static final String SUPPORT_URL = "https://www.supportkori.com/tasinpy";

    /** Original NOVA project links (credited in Settings). */
    public static final String NOVA_GITHUB_URL = "https://github.com/nova-video-player/aos-AVP";
    public static final String NOVA_DONATE_URL = "https://liberapay.com/NovaVideoPlayer/donate";

    /**
     * Socials shown in the Support popup: pairs of { label, url }.
     * Add more lines here (Facebook, YouTube, Telegram...) and they show up automatically.
     */
    public static final String[][] SOCIALS = {
            {"GitHub", GITHUB_PROFILE_URL},
    };
}
