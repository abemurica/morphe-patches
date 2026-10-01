package app.morphe.patches.backdrops

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.backdrops.shared.Constants.COMPATIBILITY_BACKDROPS
import app.morphe.patches.util.pairip.gutPairIpVm
import app.morphe.patches.util.pairip.restorePairIpHolders

// Ported from BholeyKaBhakt/android-patches-xtra's io.github.bholeykabhakt.patches.backdrops.

/**
 * Statically de-PairIPs stock Backdrops so a re-signed build runs (the native `libpairipcore.so`
 * SIGSEGVs otherwise): gut the VM + integrity/license blockers ([gutPairIpVm]), then restore the
 * VM-populated string + reflect-`Method` holder tables and `$c` bodies from the per-build
 * `backdrops/depairip_*.tsv` + `extensions/backdropsdepairip.mpe` harvest ([restorePairIpHolders] +
 * [extendWith]). Nameless dep of [unlockPremiumPatch]; regenerate the resources on a version bump.
 */
@Suppress("unused")
val disablePairipPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_BACKDROPS)

    extendWith("extensions/backdropsdepairip.mpe")

    execute {
        gutPairIpVm("Lcom/pairip/licensecheck/LicenseClient;" to "checkLicense")
        restorePairIpHolders("backdrops")
    }
}
