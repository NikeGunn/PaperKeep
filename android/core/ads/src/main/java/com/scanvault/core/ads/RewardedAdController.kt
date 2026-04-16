package com.scanvault.core.ads

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewarded ad stub for batch enhancement (3B.10).
 *
 * Shows a rewarded ad and calls [onRewarded] when the user earns the reward.
 * Phase 3B stub — no real ad SDK loaded; [onRewarded] is called immediately.
 */
@Singleton
class RewardedAdController @Inject constructor() {

    /**
     * Show a rewarded ad.
     *
     * @param onRewarded Called when the user earns the reward.
     * @param onDismissed Called when the ad is closed regardless of reward.
     */
    fun showRewardedAd(
        onRewarded: () -> Unit,
        onDismissed: () -> Unit = {},
    ) {
        // Stub: immediately reward (no real ad)
        onRewarded()
        onDismissed()
    }
}
