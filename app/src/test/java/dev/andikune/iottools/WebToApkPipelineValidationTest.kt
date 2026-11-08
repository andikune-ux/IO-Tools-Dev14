package dev.andikune.iottools

import dev.andikune.iottools.builder.WebToApkConfig
import dev.andikune.iottools.builder.WebToApkPipeline
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebToApkPipelineValidationTest {
    @Test
    fun generatedActivityWaitsForRuntimePermissionResults() {
        val generated = WebToApkPipeline.generateTemplateMainActivity(WebToApkConfig())

        assertTrue(generated.contains("pendingWebPermissionRequest"))
        assertTrue(generated.contains("override fun onRequestPermissionsResult"))
        assertTrue(generated.contains("request.grant(resources)"))
        assertTrue(generated.contains("pendingGeolocationCallback = callback"))
        assertFalse(generated.contains("ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 103)\n                        callback?.invoke(origin, true, false)"))
    }

    @Test
    fun manifestIncludesOnlyConfiguredLocationPermissions() {
        val coarseOnly = WebToApkPipeline.generatePatchedManifest(
            WebToApkConfig(permFineLocation = false, permCoarseLocation = true)
        )
        assertTrue(coarseOnly.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertFalse(coarseOnly.contains("android.permission.ACCESS_FINE_LOCATION"))

        val fineOnly = WebToApkPipeline.generatePatchedManifest(
            WebToApkConfig(permFineLocation = true, permCoarseLocation = false)
        )
        assertTrue(fineOnly.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(fineOnly.contains("android.permission.ACCESS_COARSE_LOCATION"))
    }
}
