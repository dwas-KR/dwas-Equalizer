package kr.dwas.dwas_EQ.backend

import android.content.Context

class AndroidEqualizerBackend(context: Context) : EqBackend {
    override val kind = BackendKind.ANDROID_EQUALIZER
    override val supportsSafeProbe = false
    private val appContext = context.applicationContext

    override fun probe(): BackendProbe = AndroidEqualizerRuntime.probe(appContext)

    override fun readGains(): BackendResult<IntArray> = AndroidEqualizerRuntime.readGains(appContext)

    override fun writeGains(gains: IntArray): BackendResult<Unit> = AndroidEqualizerRuntime.applyAndVerify(appContext, gains)

    override fun applyAndVerify(gains: IntArray): BackendResult<Unit> = AndroidEqualizerRuntime.applyAndVerify(appContext, gains)

    override fun restoreAndVerify(gains: IntArray): BackendResult<Unit> = AndroidEqualizerRuntime.restoreAndRelease()

    override fun close() {
    }
}
