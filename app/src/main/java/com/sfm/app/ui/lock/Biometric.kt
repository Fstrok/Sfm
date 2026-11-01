package com.sfm.app.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Fingerprint / face unlock is set up on this device. */
fun biometricAvailable(ctx: Context): Boolean = runCatching {
    BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}.getOrDefault(false)

/** Shows the system biometric sheet; returns false when it cannot be shown. */
fun showBiometricPrompt(ctx: Context, negative: String, onSuccess: () -> Unit, onCancel: () -> Unit = {}): Boolean {
    val activity = ctx.findActivity() as? FragmentActivity ?: return false
    return runCatching {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancel()
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Вход в SFM")
                .setSubtitle("Приложите палец или посмотрите в камеру")
                .setNegativeButtonText(negative)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                .setConfirmationRequired(false)
                .build(),
        )
    }.isSuccess
}
